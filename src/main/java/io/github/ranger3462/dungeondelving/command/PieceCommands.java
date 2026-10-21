package io.github.ranger3462.dungeondelving.command;

import static io.github.ranger3462.dungeondelving.command.Feedback.detail;
import static io.github.ranger3462.dungeondelving.command.Feedback.fail;
import static io.github.ranger3462.dungeondelving.command.Feedback.tell;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.github.ranger3462.dungeondelving.Config;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonPieces;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonPieces.Kind;
import io.github.ranger3462.dungeondelving.dungeon.layout.PieceTemplates;
import io.github.ranger3462.dungeondelving.dungeon.layout.PieceValidator;
import io.github.ranger3462.dungeondelving.dungeon.layout.PieceValidator.Problem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Commands for builders to check and save dungeon pieces:
 * <ul>
 * <li>{@code /dungeondelving room template <kind> <corner>} builds a starting frame to build the piece from.</li>
 * <li>{@code /dungeondelving room check <kind> <corner>} checks the piece built at that minimum corner.</li>
 * <li>{@code /dungeondelving room export <kind> <name> <corner>} checks it, then saves it as
 * {@code <kind>_<name>.nbt} in this world's generated structures, where dungeons in this world pick it up.</li>
 * </ul>
 * Kinds are {@code start}, {@code common}, {@code quarry}, {@code treasure}, {@code challenge}, {@code boss}, {@code tunnel} and
 * {@code cap}.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class PieceCommands {
    private static final int MAX_PROBLEMS_SHOWN = 8;

    private PieceCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> template = Commands.literal("template");
        LiteralArgumentBuilder<CommandSourceStack> check = Commands.literal("check");
        LiteralArgumentBuilder<CommandSourceStack> export = Commands.literal("export");
        for (Kind kind : Kind.values()) {
            String kindName = kind.name().toLowerCase(Locale.ROOT);
            template.then(Commands.literal(kindName)
                    .then(Commands.argument("corner", BlockPosArgument.blockPos())
                            .executes(context -> template(context, kind))));
            check.then(Commands.literal(kindName)
                    .then(Commands.argument("corner", BlockPosArgument.blockPos())
                            .executes(context -> check(context, kind))));
            export.then(Commands.literal(kindName)
                    .then(Commands.argument("name", StringArgumentType.word())
                            .then(Commands.argument("corner", BlockPosArgument.blockPos())
                                    .executes(context -> export(context, kind, StringArgumentType.getString(context, "name"))))));
        }
        dispatcher.register(Commands.literal(DungeonDelving.MODID)
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("room").then(template).then(check).then(export)));
    }

    private static int template(CommandContext<CommandSourceStack> context, Kind kind) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        BlockPos corner = BlockPosArgument.getLoadedBlockPos(context, "corner");
        String kindName = kind.name().toLowerCase(Locale.ROOT);
        Vec3i size = kind.size();
        Optional<BlockPos> inTheWay = PieceTemplates.firstBlockInTheWay(kind, source.getLevel(), corner);
        if (inTheWay.isPresent()) {
            fail(source, "The " + sizeText(size) + " space at " + corner.toShortString() + " isn't empty (block at "
                    + inTheWay.get().toShortString() + "). Templates only go in empty space, so nothing gets overwritten.");
            return 0;
        }
        PieceTemplates.build(kind, source.getLevel(), corner);
        tell(source, "Placed a " + kindName + " template (" + sizeText(size) + ") at " + corner.toShortString() + ".");
        detail(source, "Light gray is a starting point to replace. Red wool outside the piece marks doorways that must stay clear.");
        detail(source, "Check it with: /dungeondelving room check " + kindName + " " + corner.getX() + " " + corner.getY() + " " + corner.getZ());
        return 1;
    }

    private static int check(CommandContext<CommandSourceStack> context, Kind kind) throws CommandSyntaxException {
        BlockPos corner = BlockPosArgument.getLoadedBlockPos(context, "corner");
        return report(context.getSource(), kind, corner) ? 1 : 0;
    }

    private static int export(CommandContext<CommandSourceStack> context, Kind kind, String name) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        BlockPos corner = BlockPosArgument.getLoadedBlockPos(context, "corner");
        if (!name.matches("[a-z0-9_]+")) {
            fail(source, "Piece names can only use lowercase letters, numbers and underscores.");
            return 0;
        }
        if (!report(source, kind, corner)) {
            fail(source, "Not exported. Fix the problems above, then export again.");
            return 0;
        }

        String fileName = kind.prefix() + name;
        ResourceLocation id = DungeonPieces.id(fileName);
        ServerLevel level = source.getLevel();
        StructureTemplateManager manager = level.getStructureManager();
        StructureTemplate template = manager.getOrCreate(id);
        // Air is left out, so empty space in a piece never erases anything when it is placed.
        template.fillFromWorld(level, corner, kind.size(), false, Blocks.AIR);
        template.setAuthor(source.getTextName());
        if (!manager.save(id)) {
            fail(source, "Couldn't save " + fileName + ".nbt. Check the game log for details.");
            return 0;
        }

        Path saved = manager.createAndValidatePathToGeneratedStructure(id, ".nbt");
        tell(source, "Exported " + fileName + ".nbt. Dungeons in this world will now use it.");
        detail(source, "Saved to: " + saved.toAbsolutePath().normalize());
        copyIntoProject(source, saved, fileName);
        copyToConfiguredFolder(source, saved, fileName);
        return 1;
    }

    /** Checks a piece and reports the result. Returns whether it passed. */
    private static boolean report(CommandSourceStack source, Kind kind, BlockPos corner) {
        String kindName = kind.name().toLowerCase(Locale.ROOT);
        Vec3i size = kind.size();
        List<Problem> problems = PieceValidator.validate(kind, source.getLevel(), corner);
        if (problems.isEmpty()) {
            tell(source, "The " + kindName + " piece at " + corner.toShortString() + " (" + sizeText(size) + ") meets the spec.");
            return true;
        }
        fail(source, "The " + kindName + " piece at " + corner.toShortString() + " has " + problems.size() + " problem"
                + (problems.size() == 1 ? "" : "s") + ":");
        problems.stream().limit(MAX_PROBLEMS_SHOWN).forEach(problem -> detail(source, problem.describe(corner)));
        if (problems.size() > MAX_PROBLEMS_SHOWN) {
            detail(source, "...and " + (problems.size() - MAX_PROBLEMS_SHOWN) + " more");
        }
        return false;
    }

    private static String sizeText(Vec3i size) {
        return size.getX() + "x" + size.getY() + "x" + size.getZ();
    }

    /** Copies the export to the configured export copy folder, if one is set. */
    private static void copyToConfiguredFolder(CommandSourceStack source, Path saved, String fileName) {
        String folder = Config.EXPORT_COPY_FOLDER.get().trim();
        if (folder.isEmpty()) {
            return;
        }
        Path target = Path.of(folder).resolve(fileName + ".nbt");
        try {
            Files.createDirectories(target.getParent());
            Files.copy(saved, target, StandardCopyOption.REPLACE_EXISTING);
            detail(source, "Copied to: " + target.toAbsolutePath().normalize());
        } catch (IOException | RuntimeException e) {
            DungeonDelving.LOGGER.error("Failed to copy {} to the export copy folder {}", saved, folder, e);
            fail(source, "Exported, but couldn't copy it to " + folder + ". Check exportCopyFolder in config/dungeondelving-common.toml.");
        }
    }

    /** In a development run from the project, also copies the export into the mod's resources. */
    private static void copyIntoProject(CommandSourceStack source, Path saved, String fileName) {
        if (FMLEnvironment.production) {
            return;
        }
        Path resources = FMLPaths.GAMEDIR.get().toAbsolutePath().getParent().resolve("src/main/resources");
        if (!Files.isDirectory(resources)) {
            return;
        }
        Path target = resources.resolve("data").resolve(DungeonDelving.MODID).resolve("structure").resolve(DungeonPieces.FOLDER)
                .resolve(fileName + ".nbt");
        try {
            Files.createDirectories(target.getParent());
            Files.copy(saved, target, StandardCopyOption.REPLACE_EXISTING);
            detail(source, "Copied into the project: " + resources.getParent().relativize(target));
        } catch (IOException e) {
            DungeonDelving.LOGGER.error("Failed to copy {} into the project", saved, e);
            fail(source, "Exported, but couldn't copy it into the project. Check the game log.");
        }
    }
}
