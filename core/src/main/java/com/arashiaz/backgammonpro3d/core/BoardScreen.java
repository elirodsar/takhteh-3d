package com.arashiaz.backgammonpro3d.core;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.environment.*;
import com.badlogic.gdx.graphics.g3d.utils.*;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.FacedCubemapData;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;

/**
 * Backgammon Pro 3D - polished board prototype.
 *
 * Dark walnut furniture board: rich brown wood frame and playfield, deep
 * chocolate/ivory points, glossy chocolate and ivory checker discs, clean
 * bone dice with bold black pips, and slim brass hardware.
 * Geometry remains reusable to keep draw calls and mobile GPU memory under control.
 */
public final class BoardScreen extends ScreenAdapter implements InputProcessor {
    private static final float[] XS = {-5.775f, -4.725f, -3.675f, -2.625f, -1.575f, -0.525f,
                                       0.525f,  1.575f,  2.625f,  3.675f,  4.725f,  5.775f};
    private final PerspectiveCamera camera = new PerspectiveCamera(38f, 1f, 1f);
    private final ModelBatch batch = new ModelBatch();
    private final Environment environment = new Environment();
    private final Array<ModelInstance> models = new Array<>();
    private final Array<ModelInstance> gameObjects = new Array<>();
    private final Array<ModelInstance> moveMarkers = new Array<>();

    // Real backgammon state: positive = Light, negative = Dark.
    private final int[] points = new int[24];
    private int lightBar, darkBar;
    private int lightOff, darkOff;
    private boolean lightTurn = true;
    private final int[] dice = {0, 0, 0, 0};
    private final boolean[] dieUsed = {true, true, true, true};
    private boolean selectedBar;
    private boolean openingRollPending;
    private boolean diceRolled;
    private int selectedPoint = -1;
    private int selectedDie = -1;

    private final ShapeRenderer uiShape = new ShapeRenderer();
    private final SpriteBatch uiBatch = new SpriteBatch();
    private final BitmapFont uiFont = new BitmapFont();
    private final GlyphLayout uiLayout = new GlyphLayout();
    private boolean uiTouch;
    private float downX, downY;
    private boolean dragged;
    private boolean diceTouch;
    private float diceThrowStrength;
    private float diceSwipeDistance;
    private String status = "Roll the dice to start";
    private float statusTimer;
    private final ModelBuilder mb = new ModelBuilder();

    private Model floorModel, baseModel, playingSurfaceModel, railModel, barModel;
    private Model sideTrayModel, sideTrayInsetModel, hingePlateModel, medallionModel, medallionRingModel;
    private Model hudHeaderModel, hudHeaderInsetModel, hudTurnModel;
    private Model darkPointModel, lightPointModel, pointShadowModel;
    private Model darkChecker, lightChecker;
    private Model diceModel, checkerShadowModel;
    private Model accentModel;
    private Model diceTrayModel, screwModel;
    private Model diceEdgeModel;
    private Model pointRailModel;
    private Model doublingCubeModel;
    private Material doublingCubeTopMaterial;
    private ModelInstance doublingCubeInstance;
    private Texture woodGrainTexture, woodNormalTexture;
    private Texture lightCheckerTexture, lightCheckerNormalTexture;
    private Texture darkCheckerTexture, darkCheckerNormalTexture;
    private Texture damaskTexture, checkerShadowTexture;
    private final Texture[] dieFaceTextures = new Texture[6];
    private Texture doublingCubeTexture;
    private Cubemap studioCubemap;
    private int doublingCubeValue = 64;
    
    private float lastX, lastY;
    private boolean dragging;
    private float cameraAzimuth = 0f;
    private float cameraElevation = 66f;
    private float cameraDistance = 18.6f;

    private final Vector3 cameraTarget = new Vector3(0f, 0.50f, 0f);
    private final Vector3 tmp = new Vector3();
    private ModelInstance dieInstanceA, dieInstanceB;
    private float diceRollTime;
    private float diceRollElapsed;
    private float lastDiceLiftA;
    private float lastDiceLiftB;
    private float diceSpinA;
    private float diceSpinB;
    private int rollingFaceA = 1;
    private int rollingFaceB = 1;
    private ModelInstance movingPiece;
    private final Vector3 moveStart = new Vector3();
    private final Vector3 moveEnd = new Vector3();
    private float moveTime;
    private static final float MOVE_DURATION = 0.34f;
    private boolean moveAnimating;

    // Shared checker-stack layout: columns sit on the wide end of the point,
    // each disc rests on the previous one in a legal vertical stack. The
    // fresh checker mesh is 2*(0.085+0.030) = 0.23 units thick; the step
    // leaves a deliberate 0.025-unit separation so the rims never interpenetrate.
    private static final float STACK_Z = 3.05f;      // wide end of each point
    private static final float STACK_Y0 = 0.44f;     // point top .305 + clearance + half-thickness
    private static final float STACK_STEP = 0.255f;  // vertical spacing between discs

    @Override
    public void show() {
        Gdx.input.setInputProcessor(this);
        uiFont.getData().setScale(1.12f);
        resetGameState();

        environment.set(new ColorAttribute(
                ColorAttribute.AmbientLight, 0.29f, 0.29f, 0.31f, 1f));
        // Lower, neutral studio lighting prevents the playfield from blowing
        // out to cream while preserving readable ivory dice and checker rims.
        environment.add(new DirectionalLight().set(
                1.04f, 0.96f, 0.84f, -0.52f, -1.0f, -0.28f));
        environment.add(new DirectionalLight().set(
                0.14f, 0.16f, 0.19f, 0.48f, -0.58f, 0.64f));
        environment.add(new DirectionalLight().set(
                0.055f, 0.045f, 0.032f, 0.05f, -0.35f, -0.94f));

        woodGrainTexture = new Texture(
                Gdx.files.internal("textures/board_wood_texture.jpg"), true);
        woodGrainTexture.setFilter(
                Texture.TextureFilter.MipMapLinearLinear,
                Texture.TextureFilter.Linear);
        woodGrainTexture.setWrap(
                Texture.TextureWrap.Repeat,
                Texture.TextureWrap.Repeat);
        // Premium palette: aged ivory and dark chocolate checkers. The playfield
        // itself is a baked bronze-gray damask rather than another wood panel.
        lightCheckerTexture = createPieceTexture(256, 0.76f, 0.70f, 0.59f, 0.92f, 0.87f, 0.77f, 101L);
        darkCheckerTexture = createPieceTexture(256, 0.16f, 0.035f, 0.045f, 0.46f, 0.13f, 0.15f, 202L);
        damaskTexture = createDamaskTexture(1024, 512);
        checkerShadowTexture = createSoftShadowTexture(96);
        for (int i = 0; i < dieFaceTextures.length; i++) {
            dieFaceTextures[i] = createDieFaceTexture(96, i + 1);
        }
        doublingCubeTexture = createDoublingCubeTexture(doublingCubeValue);
        woodNormalTexture = createNormalTexture(256, 11f, 0.55f, 404L);
        lightCheckerNormalTexture = createNormalTexture(256, 5f, 0.34f, 505L);
        darkCheckerNormalTexture = createNormalTexture(256, 5f, 0.34f, 606L);
        studioCubemap = createStudioCubemap();
        buildBoard();
        updateCamera();
    }


    private void resetGameState() {
        for (int i = 0; i < 24; i++) points[i] = 0;

        // Standard opening position. Light moves 1 -> 24 (home: 19-24),
        // Dark moves 24 -> 1 (home: 1-6). Each side holds:
        // 2 on the opponent's ace, 5 on its own 6-point,
        // 3 on its own 8-point, 5 on its own 13-point (midpoint).
        // Light: 2 on 1, 5 on 12 (mid), 3 on 17, 5 on 19.
        // Dark:  2 on 24, 5 on 13 (mid), 3 on 8, 5 on 6.
        points[0] = 2;
        points[11] = 5;
        points[16] = 3;
        points[18] = 5;
        points[23] = -2;
        points[12] = -5;
        points[7] = -3;
        points[5] = -5;

        lightBar = darkBar = lightOff = darkOff = 0;
        lightTurn = true;
        for (int i = 0; i < 4; i++) {
            dice[i] = 0;
            dieUsed[i] = true;
        }
        diceRolled = false;
        selectedPoint = -1;
        selectedBar = false;
        openingRollPending = true;
        doublingCubeValue = 64;
        status = "Roll the dice to start";
        statusTimer = 0f;
    }

    private boolean allDiceUsed() {
        return dieUsed[0] && dieUsed[1] && dieUsed[2] && dieUsed[3];
    }

    private int barCount() {
        return lightTurn ? lightBar : darkBar;
    }

    private boolean owns(int point) {
        return point >= 0 && point < 24 && (lightTurn ? points[point] > 0 : points[point] < 0);
    }

    private boolean openPoint(int to) {
        if (to < 0 || to >= 24) return false;
        return lightTurn ? points[to] >= -1 : points[to] <= 1;
    }

    private int entryPoint(int die) {
        // A hit checker always re-enters in the OPPONENT's home board:
        // Light (moving up) enters low at points 1-6, Dark enters high at 19-24.
        return lightTurn ? die - 1 : 24 - die;
    }

    private boolean canEnterWithDie(int die) {
        int to = entryPoint(die);
        return die >= 1 && die <= 6 && openPoint(to);
    }

    private boolean allCheckersHome() {
        if (barCount() > 0) return false;
        if (lightTurn) {
            for (int p = 0; p < 18; p++) if (points[p] > 0) return false;
        } else {
            for (int p = 6; p < 24; p++) if (points[p] < 0) return false;
        }
        return true;
    }

    private boolean canBearOffWithDie(int from, int die) {
        if (!owns(from) || !allCheckersHome()) return false;
        if (lightTurn) {
            int distance = 24 - from;
            if (die == distance) return true;
            if (die < distance) return false;
            for (int p = from + 1; p < 24; p++) if (points[p] > 0) return false;
            return true;
        } else {
            int distance = from + 1;
            if (die == distance) return true;
            if (die < distance) return false;
            for (int p = from - 1; p >= 0; p--) if (points[p] < 0) return false;
            return true;
        }
    }

    private int moveDistance(int from, int to) {
        return lightTurn ? to - from : from - to;
    }

    private boolean canMoveWithDie(int from, int to, int die) {
        if (!owns(from) || !openPoint(to) || moveDistance(from, to) != die) return false;
        return !canBearOffWithDie(from, die) || to >= 0 && to < 24;
    }

    private boolean canUseDie(int from, int die) {
        if (barCount() > 0) return false;
        int to = lightTurn ? from + die : from - die;
        return canMoveWithDie(from, to, die) || canBearOffWithDie(from, die);
    }

    private boolean hasAnyMoveForDie(int die) {
        if (die < 1 || die > 6) return false;
        if (barCount() > 0) return canEnterWithDie(die);
        for (int p = 0; p < 24; p++) {
            if (owns(p) && canUseDie(p, die)) return true;
        }
        return false;
    }

    private boolean hasAnyMove() {
        for (int d = 0; d < 4; d++) {
            if (!dieUsed[d] && hasAnyMoveForDie(dice[d])) return true;
        }
        return false;
    }

    /**
     * Full move-order legality search for the current roll.
     *
     * Instead of looking only at the current board, this explores the legal
     * continuations for the remaining dice. This matters for positions where
     * playing die A first can make die B unavailable, while the reverse order
     * allows both dice to be used.
     */
    private boolean dieAllowedByTurn(int dieIndex) {
        if (dieIndex < 0 || dieIndex >= 4 || dieUsed[dieIndex]) return false;

        RuleState state = captureRuleState();
        int best = maxUsableMoves(state);

        if (best <= 0) return false;

        // When only one move can be made from a two-die roll, the larger
        // playable number is mandatory. Doubles have four equivalent moves.
        if (best == 1) {
            int largestPlayable = -1;
            boolean anyOtherValue = false;
            for (int d = 0; d < 4; d++) {
                if (state.used[d]) continue;
                if (!hasLegalMove(state, d)) continue;
                largestPlayable = Math.max(largestPlayable, dice[d]);
                anyOtherValue = anyOtherValue || dice[d] != dice[dieIndex];
            }
            if (anyOtherValue && dice[dieIndex] != largestPlayable) return false;
        }

        // The selected die is legal only if there is a complete continuation
        // that still reaches the maximum number of usable moves.
        for (RuleMove move : legalMoves(state, dieIndex)) {
            RuleState next = state.copy();
            applyRuleMove(next, move);
            next.used[dieIndex] = true;
            if (1 + maxUsableMoves(next) == best) return true;
        }
        return false;
    }

    private static final class RuleState {
        final int[] points = new int[24];
        int lightBar, darkBar, lightOff, darkOff;
        final boolean[] used = new boolean[4];

        RuleState copy() {
            RuleState s = new RuleState();
            System.arraycopy(points, 0, s.points, 0, 24);
            s.lightBar = lightBar;
            s.darkBar = darkBar;
            s.lightOff = lightOff;
            s.darkOff = darkOff;
            System.arraycopy(used, 0, s.used, 0, 4);
            return s;
        }
    }

    private static final class RuleMove {
        final int from;
        final int to;
        final boolean bearOff;

        RuleMove(int from, int to, boolean bearOff) {
            this.from = from;
            this.to = to;
            this.bearOff = bearOff;
        }
    }

    private RuleState captureRuleState() {
        RuleState s = new RuleState();
        System.arraycopy(points, 0, s.points, 0, 24);
        s.lightBar = lightBar;
        s.darkBar = darkBar;
        s.lightOff = lightOff;
        s.darkOff = darkOff;
        System.arraycopy(dieUsed, 0, s.used, 0, 4);
        return s;
    }

    private int stateBarCount(RuleState s) {
        return lightTurn ? s.lightBar : s.darkBar;
    }

    private boolean stateOwns(RuleState s, int point) {
        return point >= 0 && point < 24
                && (lightTurn ? s.points[point] > 0 : s.points[point] < 0);
    }

    private boolean stateOpenPoint(RuleState s, int to) {
        if (to < 0 || to >= 24) return false;
        return lightTurn ? s.points[to] >= -1 : s.points[to] <= 1;
    }

    private int stateEntryPoint(int die) {
        return lightTurn ? die - 1 : 24 - die;
    }

    private boolean stateCanEnter(RuleState s, int die) {
        int to = stateEntryPoint(die);
        return die >= 1 && die <= 6 && stateOpenPoint(s, to);
    }

    private boolean stateAllHome(RuleState s) {
        if (stateBarCount(s) > 0) return false;
        if (lightTurn) {
            for (int p = 0; p < 18; p++) if (s.points[p] > 0) return false;
        } else {
            for (int p = 6; p < 24; p++) if (s.points[p] < 0) return false;
        }
        return true;
    }

    private boolean stateCanBearOff(RuleState s, int from, int die) {
        if (!stateOwns(s, from) || !stateAllHome(s)) return false;
        if (lightTurn) {
            int distance = 24 - from;
            if (die == distance) return true;
            if (die < distance) return false;
            for (int p = from + 1; p < 24; p++) if (s.points[p] > 0) return false;
            return true;
        } else {
            int distance = from + 1;
            if (die == distance) return true;
            if (die < distance) return false;
            for (int p = from - 1; p >= 0; p--) if (s.points[p] < 0) return false;
            return true;
        }
    }

    private boolean stateCanMove(RuleState s, int from, int to, int die) {
        if (!stateOwns(s, from) || !stateOpenPoint(s, to)) return false;
        int distance = lightTurn ? to - from : from - to;
        return distance == die;
    }

    private Array<RuleMove> legalMoves(RuleState s, int dieIndex) {
        Array<RuleMove> result = new Array<>();
        if (dieIndex < 0 || dieIndex >= 4 || s.used[dieIndex]) return result;

        int die = dice[dieIndex];
        if (die < 1 || die > 6) return result;

        if (stateBarCount(s) > 0) {
            if (stateCanEnter(s, die)) {
                result.add(new RuleMove(-1, stateEntryPoint(die), false));
            }
            return result;
        }

        for (int from = 0; from < 24; from++) {
            if (!stateOwns(s, from)) continue;
            int to = lightTurn ? from + die : from - die;
            if (stateCanMove(s, from, to, die)) {
                result.add(new RuleMove(from, to, false));
            }
            if (stateCanBearOff(s, from, die)) {
                result.add(new RuleMove(from, -1, true));
            }
        }
        return result;
    }

    private boolean hasLegalMove(RuleState s, int dieIndex) {
        return legalMoves(s, dieIndex).size > 0;
    }

    private int maxUsableMoves(RuleState s) {
        int best = 0;
        boolean found = false;

        for (int d = 0; d < 4; d++) {
            if (s.used[d]) continue;
            Array<RuleMove> moves = legalMoves(s, d);
            for (RuleMove move : moves) {
                found = true;
                RuleState next = s.copy();
                applyRuleMove(next, move);
                next.used[d] = true;
                best = Math.max(best, 1 + maxUsableMoves(next));
            }
        }
        return found ? best : 0;
    }

    private void applyRuleMove(RuleState s, RuleMove move) {
        if (move.bearOff) {
            if (lightTurn) {
                s.points[move.from]--;
                s.lightOff++;
            } else {
                s.points[move.from]++;
                s.darkOff++;
            }
            return;
        }

        if (move.from == -1) {
            if (lightTurn) {
                s.lightBar--;
                if (s.points[move.to] == -1) {
                    s.points[move.to] = 0;
                    s.darkBar++;
                }
                s.points[move.to]++;
            } else {
                s.darkBar--;
                if (s.points[move.to] == 1) {
                    s.points[move.to] = 0;
                    s.lightBar++;
                }
                s.points[move.to]--;
            }
            return;
        }

        if (lightTurn) {
            if (s.points[move.to] == -1) {
                s.points[move.to] = 0;
                s.darkBar++;
            }
            s.points[move.from]--;
            s.points[move.to]++;
        } else {
            if (s.points[move.to] == 1) {
                s.points[move.to] = 0;
                s.lightBar++;
            }
            s.points[move.from]++;
            s.points[move.to]--;
        }
    }

    private int hitDie(int screenX, int screenY) {
        Ray ray = camera.getPickRay(screenX, screenY);
        // Intersect at the top of the dice/tray region. Using the actual
        // projected die centers avoids the old board-scale mismatch that made
        // only a small corner respond to touch.
        Plane plane = new Plane(Vector3.Y, 0.93f);
        if (!Intersector.intersectRayPlane(ray, plane, tmp)) return -1;
        final float r2 = 0.43f * 0.43f;
        float dxA = tmp.x + 0.48f;
        float dxB = tmp.x - 0.48f;
        if (dxA * dxA + tmp.z * tmp.z <= r2) return 0;
        if (dxB * dxB + tmp.z * tmp.z <= r2) return 1;
        return -1;
    }

    private void rollDice() {
        rollDice(0f);
    }

    private void rollDice(float throwStrength) {
        if (diceRolled && !allDiceUsed()) {
            status = "Use the current dice first";
            statusTimer = 1.1f;
            return;
        }

        dice[0] = MathUtils.random(1, 6);
        dice[1] = MathUtils.random(1, 6);

        // Opening roll: the higher die determines who starts, and both
        // numbers are used immediately. Ties are rolled again.
        if (openingRollPending) {
            while (dice[1] == dice[0]) dice[1] = MathUtils.random(1, 6);
            lightTurn = dice[0] > dice[1];
            openingRollPending = false;
        }

        dice[2] = dice[0] == dice[1] ? dice[0] : 0;
        dice[3] = dice[0] == dice[1] ? dice[0] : 0;
        for (int i = 0; i < 4; i++) dieUsed[i] = (i >= 2 && dice[i] == 0);
        dieUsed[0] = dieUsed[1] = false;

        rollingFaceA = MathUtils.random(1, 6);
        rollingFaceB = MathUtils.random(1, 6);
        diceRolled = true;
        diceRollElapsed = 0f;
        lastDiceLiftA = 0f;
        lastDiceLiftB = 0f;
        diceThrowStrength = MathUtils.clamp(throwStrength, 0f, 650f);
        diceSpinA = MathUtils.random(0f, 360f);
        diceSpinB = MathUtils.random(0f, 360f);
        diceRollTime = 0.95f;
        selectedPoint = -1;
        selectedDie = -1;
        clearMoveMarkers();
        status = lightTurn ? "Light: choose a checker" : "Dark: choose a checker";
        statusTimer = 1.5f;
        rebuildGameObjects();
    }

    private void rebuildGameObjects() {
        for (ModelInstance instance : gameObjects) models.removeValue(instance, true);
        gameObjects.clear();

        addStateStacks();

        // The pips are baked into all six faces, so the dice remain legible
        // during the roll and never look like two blank paper cards. Once the
        // animation settles, rotate the correct numbered face to the top.
        dieInstanceA = new ModelInstance(diceModel, -0.48f, 0.93f, 0f);
        dieInstanceB = new ModelInstance(diceModel,  0.48f, 0.93f, 0f);
        if (diceRollTime <= 0f) {
            setDieFace(dieInstanceA, dice[0] > 0 ? dice[0] : 1);
            setDieFace(dieInstanceB, dice[1] > 0 ? dice[1] : 1);
        }
        gameObjects.add(dieInstanceA); models.add(dieInstanceA);
        gameObjects.add(dieInstanceB); models.add(dieInstanceB);
    }

    private void addStateStacks() {
        for (int p = 0; p < 24; p++) {
            int count = Math.abs(points[p]);
            if (count == 0) continue;
            Model model = points[p] > 0 ? lightChecker : darkChecker;
            int col = p < 12 ? p : 23 - p;
            float x = XS[col];
            float z = p < 12 ? -STACK_Z : STACK_Z;
            // One contact shadow per column; discs then stack exactly one
            // thickness apart so none of them clips into another.
            ModelInstance shadow = new ModelInstance(
                    checkerShadowModel, x + 0.065f, 0.312f, z + 0.045f);
            shadow.transform.scl(1.62f, 1f, 1.12f);
            gameObjects.add(shadow); models.add(shadow);
            for (int i = 0; i < count; i++) {
                ModelInstance piece = new ModelInstance(model, x, STACK_Y0 + i * STACK_STEP, z);
                gameObjects.add(piece); models.add(piece);
            }
        }
        // Bar checkers rest on top of the central bar cap, clear of the dice tray.
        for (int i = 0; i < lightBar; i++) {
            ModelInstance piece = new ModelInstance(
                    lightChecker, 0f, 0.60f + i * STACK_STEP, -2.6f);
            gameObjects.add(piece); models.add(piece);
        }
        for (int i = 0; i < darkBar; i++) {
            ModelInstance piece = new ModelInstance(
                    darkChecker, 0f, 0.60f + i * STACK_STEP, 2.6f);
            gameObjects.add(piece); models.add(piece);
        }

        addBorneOffCheckers(lightChecker, lightOff, true);
        addBorneOffCheckers(darkChecker, darkOff, false);
    }

    private void addBorneOffCheckers(Model model, int count, boolean light) {
        // Borne-off checkers stack in five low piles of three inside their own
        // zone of the right-side storage: bottom zone for White, top for Black.
        int capped = Math.min(15, Math.max(0, count));
        float zoneCenterZ = light ? -1.92f : 1.92f;
        for (int i = 0; i < capped; i++) {
            int pile = i / 3;
            int level = i % 3;
            float x = 7.02f;
            float z = zoneCenterZ + (pile - 2) * 0.62f;

            if (level == 0) {
                ModelInstance shadow = new ModelInstance(
                        checkerShadowModel, x + 0.045f, 0.229f, z + 0.035f);
                shadow.transform.scl(1.05f, 1f, 0.86f);
                gameObjects.add(shadow);
                models.add(shadow);
            }

            ModelInstance piece = new ModelInstance(model, x, 0.306f + level * 0.16f, z);
            piece.transform.scl(0.62f, 0.72f, 0.62f);
            gameObjects.add(piece);
            models.add(piece);
        }
    }

    private ModelInstance findTopChecker(int point) {
        int count = Math.abs(points[point]);
        if (count <= 0) return null;
        int col = point < 12 ? point : 23 - point;
        float x = XS[col];
        float z = point < 12 ? -STACK_Z : STACK_Z;
        Model expected = points[point] > 0 ? lightChecker : darkChecker;
        ModelInstance top = null;
        float highestY = -Float.MAX_VALUE;
        for (ModelInstance instance : gameObjects) {
            if (instance.model != expected) continue;
            instance.transform.getTranslation(tmp);
            if (Math.abs(tmp.x - x) < 0.08f && Math.abs(tmp.z - z) < 0.08f
                    && tmp.y > highestY) {
                highestY = tmp.y;
                top = instance;
            }
        }
        return top;
    }

    private Vector3 pointPosition(int point, int stackIndex) {
        int col = point < 12 ? point : 23 - point;
        float x = XS[col];
        float z = point < 12 ? -STACK_Z : STACK_Z;
        return new Vector3(x, STACK_Y0 + stackIndex * STACK_STEP, z);
    }

    private void startMoveAnimation(ModelInstance piece, int from, int destination, int sourceStackIndex) {
        if (piece == null) {
            rebuildGameObjects();
            finishMoveState();
            return;
        }
        moveStart.set(pointPosition(from, sourceStackIndex));
        int destinationCountBefore = Math.abs(points[destination]);
        int destinationStackIndex = (points[destination] != 0 && ((points[destination] > 0) == lightTurn))
                ? destinationCountBefore - 1 : destinationCountBefore;
        moveEnd.set(pointPosition(destination, Math.max(0, destinationStackIndex)));
        moveEnd.y = STACK_Y0 + Math.max(0, destinationStackIndex) * STACK_STEP;

        models.removeValue(piece, true);
        gameObjects.removeValue(piece, true);
        movingPiece = piece;
        movingPiece.transform.setToTranslation(moveStart);
        models.add(movingPiece);
        moveTime = 0f;
        moveAnimating = true;
    }

    private void finishMoveState() {
        rebuildGameObjects();
        if (allDiceUsed() || !hasAnyMove()) {
            lightTurn = !lightTurn;
            diceRolled = false;
            for (int i = 0; i < 4; i++) dieUsed[i] = true;
            selectedBar = false;
            status = lightTurn ? "Light's turn — roll" : "Dark's turn — roll";
        } else {
            status = lightTurn ? "Light: choose your next move" : "Dark: choose your next move";
        }
        statusTimer = 1.1f;
    }

    private void selectPoint(int point) {
        if (!diceRolled || allDiceUsed()) return;
        if (barCount() > 0) {
            selectedBar = true;
            selectedPoint = -1;
            status = "Enter your checker from the bar";
            statusTimer = 0.9f;
            rebuildMoveMarkers();
            return;
        }
        if (!owns(point)) {
            status = "Select your checker";
            statusTimer = 0.9f;
            return;
        }
        boolean movable = false;
        for (int d = 0; d < 4; d++) {
            if (selectedDie >= 0 && d != selectedDie) continue;
            if (!dieUsed[d] && dieAllowedByTurn(d) && canUseDie(point, dice[d])) movable = true;
        }
        if (!movable) {
            status = "No legal move for this checker";
            statusTimer = 0.9f;
            return;
        }
        selectedPoint = selectedPoint == point ? -1 : point;
        selectedBar = false;
        rebuildMoveMarkers();
    }

    private void rebuildMoveMarkers() {
        clearMoveMarkers();
        if (!diceRolled || allDiceUsed()) return;

        if (selectedBar) {
            for (int d = 0; d < 4; d++) {
                if (selectedDie >= 0 && d != selectedDie) continue;
                if (dieUsed[d] || !dieAllowedByTurn(d) || !canEnterWithDie(dice[d])) continue;
                int to = entryPoint(dice[d]);
                int col = to < 12 ? to : 23 - to;
                float z = to < 12 ? -2.9f : 2.9f;
                moveMarkers.add(new ModelInstance(accentModel, XS[col], 0.35f, z));
            }
            return;
        }

        if (selectedPoint < 0) return;
        for (int d = 0; d < 4; d++) {
            if (selectedDie >= 0 && d != selectedDie) continue;
            if (dieUsed[d] || !dieAllowedByTurn(d)) continue;
            int to = lightTurn ? selectedPoint + dice[d] : selectedPoint - dice[d];
            if (canMoveWithDie(selectedPoint, to, dice[d])) {
                int col = to < 12 ? to : 23 - to;
                float z = to < 12 ? -2.9f : 2.9f;
                moveMarkers.add(new ModelInstance(accentModel, XS[col], 0.35f, z));
            }
        }
    }

    private void clearMoveMarkers() { moveMarkers.clear(); }

    private void tryBarMove(int destination) {
        if (barCount() <= 0 || moveAnimating || diceRollTime > 0f) return;
        int dieIndex = -1;
        for (int d = 0; d < 4; d++) {
            if (selectedDie >= 0 && d != selectedDie) continue;
            if (!dieUsed[d] && dieAllowedByTurn(d)
                    && entryPoint(dice[d]) == destination
                    && canEnterWithDie(dice[d])) {
                dieIndex = d;
                break;
            }
        }
        if (dieIndex < 0) {
            status = "That entry is blocked";
            statusTimer = 0.9f;
            return;
        }

        if (lightTurn) lightBar--; else darkBar--;
        if (lightTurn && points[destination] == -1) { points[destination] = 0; darkBar++; }
        if (!lightTurn && points[destination] == 1) { points[destination] = 0; lightBar++; }
        points[destination] += lightTurn ? 1 : -1;
        dieUsed[dieIndex] = true;
        selectedBar = false;
        selectedPoint = -1;
        clearMoveMarkers();
        rebuildGameObjects();
        status = "Checker entered";
        statusTimer = 0.7f;
        if (allDiceUsed() || !hasAnyMove()) finishMoveState();
    }

    private void updateDoublingCubeValue() {
        int completedGroups = (lightOff + darkOff) / 5;
        int next = 64;
        for (int i = 0; i < completedGroups; i++) next = Math.max(1, next / 2);
        if (next == doublingCubeValue || doublingCubeModel == null) return;
        doublingCubeValue = next;
        if (doublingCubeTexture != null) doublingCubeTexture.dispose();
        doublingCubeTexture = createDoublingCubeTexture(doublingCubeValue);
        if (doublingCubeTopMaterial != null) {
            doublingCubeTopMaterial.set(TextureAttribute.createDiffuse(doublingCubeTexture));
        }
    }

    private void tryBearOff() {
        if (selectedPoint < 0 || !allCheckersHome()) return;
        int from = selectedPoint;
        int dieIndex = -1;
        for (int d = 0; d < 4; d++) {
            if (selectedDie >= 0 && d != selectedDie) continue;
            if (!dieUsed[d] && dieAllowedByTurn(d) && canBearOffWithDie(from, dice[d])) {
                dieIndex = d;
                break;
            }
        }
        if (dieIndex < 0) {
            status = "That checker cannot bear off with this roll";
            statusTimer = 0.9f;
            return;
        }

        points[from] -= lightTurn ? 1 : -1;
        if (lightTurn) lightOff++; else darkOff++;
        updateDoublingCubeValue();
        dieUsed[dieIndex] = true;
        selectedDie = -1;
        selectedPoint = -1;
        selectedBar = false;
        clearMoveMarkers();
        rebuildGameObjects();

        if (lightOff >= 15 || darkOff >= 15) {
            status = lightOff >= 15 ? "LIGHT WINS" : "DARK WINS";
            statusTimer = 999f;
            diceRolled = false;
            for (int i = 0; i < 4; i++) dieUsed[i] = true;
            return;
        }
        if (allDiceUsed() || !hasAnyMove()) finishMoveState();
    }

    private void tryMove(int destination) {
        if (moveAnimating || diceRollTime > 0f) return;

        if (selectedBar) {
            tryBarMove(destination);
            return;
        }

        if (selectedPoint < 0) {
            selectPoint(destination);
            return;
        }

        if (destination < 0 || destination >= 24) return;

        int dieIndex = -1;
        for (int d = 0; d < 4; d++) {
            if (selectedDie >= 0 && d != selectedDie) continue;
            if (!dieUsed[d] && dieAllowedByTurn(d)
                    && canMoveWithDie(selectedPoint, destination, dice[d])) {
                dieIndex = d;
                break;
            }
        }
        if (dieIndex < 0) {
            if (owns(destination)) selectPoint(destination);
            else {
                status = "That move is not allowed";
                statusTimer = 0.9f;
            }
            return;
        }

        final int from = selectedPoint;
        final int sourceStackIndex = Math.abs(points[from]) - 1;
        ModelInstance piece = findTopChecker(from);

        int sign = lightTurn ? 1 : -1;
        if (lightTurn && points[destination] == -1) { points[destination] = 0; darkBar++; }
        if (!lightTurn && points[destination] == 1) { points[destination] = 0; lightBar++; }
        points[from] -= sign;
        points[destination] += sign;
        dieUsed[dieIndex] = true;
        selectedPoint = -1;
        selectedBar = false;
        clearMoveMarkers();

        status = "Moving...";
        statusTimer = 0.8f;
        startMoveAnimation(piece, from, destination, sourceStackIndex);
    }

    private int nearestPoint(float x, float z) {
        int best = -1;
        float bestDistance = 1.05f;
        for (int p = 0; p < 24; p++) {
            int col = p < 12 ? p : 23 - p;
            float px = XS[col];
            float pz = p < 12 ? -2.9f : 2.9f;
            float distance = Vector2.dst(x, z, px, pz);
            if (distance < bestDistance) { bestDistance = distance; best = p; }
        }
        return best;
    }

    private void pickBoard(int screenX, int screenY) {
        if (moveAnimating) return;
        Ray ray = camera.getPickRay(screenX, screenY);
        Plane plane = new Plane(Vector3.Y, 0.24f);
        if (!Intersector.intersectRayPlane(ray, plane, tmp)) return;

        if (Math.abs(tmp.x) < 1.05f && Math.abs(tmp.z) < 1.35f) {
            if (barCount() > 0) {
                selectedBar = true;
                selectedPoint = -1;
                status = "Choose an entry point";
                statusTimer = 0.9f;
                rebuildMoveMarkers();
            }
            return;
        }

        if (tmp.x > 6.15f && selectedPoint >= 0) {
            tryBearOff();
            return;
        }

        int point = nearestPoint(tmp.x, tmp.z);
        if (point >= 0) tryMove(point);
    }

    private float smoothNoise(float x, float y) {
        int x0 = MathUtils.floor(x);
        int y0 = MathUtils.floor(y);
        float fx = x - x0;
        float fy = y - y0;
        fx = fx * fx * (3f - 2f * fx);
        fy = fy * fy * (3f - 2f * fy);

        float n00 = hashNoise(x0, y0);
        float n10 = hashNoise(x0 + 1, y0);
        float n01 = hashNoise(x0, y0 + 1);
        float n11 = hashNoise(x0 + 1, y0 + 1);

        float nx0 = MathUtils.lerp(n00, n10, fx);
        float nx1 = MathUtils.lerp(n01, n11, fx);
        return MathUtils.lerp(nx0, nx1, fy);
    }

    private float hashNoise(int x, int y) {
        int n = x * 374761393 + y * 668265263;
        n = (n ^ (n >>> 13)) * 1274126177;
        n ^= (n >>> 16);
        return (n & 0x7fffffff) / 2147483647f;
    }

    private Texture createNormalTexture(int size, float frequency, float strength, long seed) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            float u = x / (float)(size - 1), v = y / (float)(size - 1);
            float hL = materialHeight(u - 1f / size, v, frequency, seed);
            float hR = materialHeight(u + 1f / size, v, frequency, seed);
            float hD = materialHeight(u, v - 1f / size, frequency, seed);
            float hU = materialHeight(u, v + 1f / size, frequency, seed);
            float nx = (hL - hR) * strength, ny = (hD - hU) * strength, nz = 1f;
            float len = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
            int r = (int)((nx / len * 0.5f + 0.5f) * 255f);
            int g = (int)((ny / len * 0.5f + 0.5f) * 255f);
            int b = (int)((nz / len * 0.5f + 0.5f) * 255f);
            pm.drawPixel(x, y, Color.rgba8888(r / 255f, g / 255f, b / 255f, 1f));
        }
        Texture t = new Texture(pm, true);
        t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        pm.dispose();
        return t;
    }

    private float materialHeight(float u, float v, float frequency, long seed) {
        float x = u * frequency * 18f + (seed % 97) * 0.17f;
        float y = v * frequency * 18f + (seed % 53) * 0.11f;
        return smoothNoise(x, y) * 0.72f + smoothNoise(x * 2.7f, y * 2.7f) * 0.28f;
    }

    /**
     * Bronze-gray damask artwork for the recessed playfield. The lightmap is
     * multiplied into the pixels at generation time: this keeps the runtime
     * shader on the stock mobile DefaultShader and still gives the board a
     * deliberate perimeter falloff, column AO and bar/rail contact strips.
     */
    private Texture createDamaskTexture(int width, int height) {
        Pixmap artwork = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        Pixmap lightmap = createLightmapPixmap(width, height);
        final float[] baseColor = {0.47f, 0.44f, 0.39f};

        for (int y = 0; y < height; y++) {
            float v = y / (float)(height - 1);
            for (int x = 0; x < width; x++) {
                float u = x / (float)(width - 1);
                float cu = u * 4f;
                float cv = v * 3f;
                float fu = cu - MathUtils.floor(cu);
                float fv = cv - MathUtils.floor(cv);
                float dx = fu - 0.5f;
                float dy = fv - 0.5f;
                float radius = (float)Math.sqrt(dx * dx + dy * dy);
                float angle = (float)Math.atan2(dy, dx);

                // Quiet damask rosettes: soft radial ornament and fine noise only.
                // No horizontal lattice/stripe function is used, which removes
                // the repeated lines visible in the previous APK.
                float petals = (float)Math.pow(Math.abs(Math.cos(angle * 6f)), 2.2);
                float flower = MathUtils.clamp(1f - radius * 2.7f, 0f, 1f) * petals;
                float ring = MathUtils.clamp(1f - Math.abs(radius - 0.27f) * 9.0f, 0f, 1f);
                float motif = MathUtils.clamp(flower * 0.50f + ring * 0.18f, 0f, 1f);
                float noise = smoothNoise(u * 18.0f + 4.3f, v * 16.0f + 1.7f);
                float luminance = 0.68f + motif * 0.040f + (noise - 0.5f) * 0.018f;

                int packed = lightmap.getPixel(x, y);
                float baked = ((packed >>> 24) & 0xff) / 255f;
                float r = MathUtils.clamp(baseColor[0] * luminance * baked, 0f, 1f);
                float g = MathUtils.clamp(baseColor[1] * luminance * baked, 0f, 1f);
                float b = MathUtils.clamp(baseColor[2] * luminance * baked, 0f, 1f);
                artwork.setColor(r, g, b, 1f);
                artwork.drawPixel(x, y);
            }
        }
        lightmap.dispose();
        Texture result = new Texture(artwork, true);
        result.setFilter(Texture.TextureFilter.MipMapLinearLinear,
                Texture.TextureFilter.Linear);
        result.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        artwork.dispose();
        return result;
    }

    private Pixmap createLightmapPixmap(int width, int height) {
        Pixmap lightmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        for (int y = 0; y < height; y++) {
            float v = y / (float)(height - 1);
            for (int x = 0; x < width; x++) {
                float u = x / (float)(width - 1);
                float edgeDistance = Math.min(Math.min(u, 1f - u), Math.min(v, 1f - v));
                float edge = MathUtils.clamp(1f - edgeDistance / 0.18f, 0f, 1f);
                float factor = 1f - 0.30f * edge * edge;

                // Narrow AO at the twelve checker columns and at the two rows
                // where the stacked pieces actually touch the playfield.
                float columnAo = 0f;
                for (int i = 0; i < 12; i++) {
                    float column = (i + 0.5f) / 12f;
                    float d = (u - column) / 0.016f;
                    columnAo = Math.max(columnAo, (float)Math.exp(-d * d));
                }
                float rowTop = (float)Math.exp(-Math.pow((v - 0.105f) / 0.030f, 2.0));
                float rowBottom = (float)Math.exp(-Math.pow((v - 0.895f) / 0.030f, 2.0));
                float barAo = (float)Math.exp(-Math.pow((u - 0.5f) / 0.030f, 2.0));
                float railAo = Math.max(
                        (float)Math.exp(-Math.pow(v / 0.025f, 2.0)),
                        (float)Math.exp(-Math.pow((1f - v) / 0.025f, 2.0)));
                factor -= columnAo * 0.075f;
                factor -= Math.max(rowTop, rowBottom) * 0.115f;
                factor -= barAo * 0.13f;
                factor -= railAo * 0.10f;
                factor = MathUtils.clamp(factor, 0.54f, 1f);
                lightmap.setColor(factor, factor, factor, 1f);
                lightmap.drawPixel(x, y);
            }
        }
        return lightmap;
    }

    private Texture createSoftShadowTexture(int size) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        for (int y = 0; y < size; y++) {
            float v = (y + 0.5f) / size * 2f - 1f;
            for (int x = 0; x < size; x++) {
                float u = (x + 0.5f) / size * 2f - 1f;
                float distance = (float)Math.sqrt(u * u + v * v);
                float alpha = distance >= 1f
                        ? 0f
                        : 0.38f * (1f - distance) * (1f - distance);
                pm.setColor(0.008f, 0.006f, 0.005f, alpha);
                pm.drawPixel(x, y);
            }
        }
        Texture result = new Texture(pm, true);
        result.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        result.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        pm.dispose();
        return result;
    }

    private Texture createDieFaceTexture(int size, int number) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        for (int y = 0; y < size; y++) {
            float v = y / (float)(size - 1);
            for (int x = 0; x < size; x++) {
                float u = x / (float)(size - 1);
                float n = smoothNoise(u * 5.0f + number * 0.41f, v * 4.0f + 2.7f);
                float edge = Math.max(Math.abs(u - 0.5f), Math.abs(v - 0.5f));
                float shade = 0.92f - edge * 0.07f + (n - 0.5f) * 0.025f;
                pm.setColor(shade, shade * 0.965f, shade * 0.88f, 1f);
                pm.drawPixel(x, y);
            }
        }

        // A thin dark edge keeps the ivory face separated from adjacent faces.
        pm.setColor(0.38f, 0.22f, 0.15f, 1f);
        pm.drawRectangle(3, 3, size - 7, size - 7);
        pm.setColor(0.09f, 0.025f, 0.022f, 1f);
        int q = size / 4;
        int c = size / 2;
        int r = Math.max(7, size / 10);
        int[][] positions;
        switch (number) {
            case 1: positions = new int[][]{{c, c}}; break;
            case 2: positions = new int[][]{{q, q}, {size - q, size - q}}; break;
            case 3: positions = new int[][]{{q, q}, {c, c}, {size - q, size - q}}; break;
            case 4: positions = new int[][]{{q, q}, {size - q, q}, {q, size - q}, {size - q, size - q}}; break;
            case 5: positions = new int[][]{{q, q}, {size - q, q}, {c, c}, {q, size - q}, {size - q, size - q}}; break;
            default: positions = new int[][]{{q, q}, {size - q, q}, {q, c}, {size - q, c}, {q, size - q}, {size - q, size - q}}; break;
        }
        for (int[] position : positions) {
            pm.setColor(0.30f, 0.14f, 0.075f, 1f);
            pm.fillCircle(position[0], position[1], r + 2);
            pm.setColor(0.025f, 0.012f, 0.010f, 1f);
            pm.fillCircle(position[0], position[1], r);
            pm.setColor(0.12f, 0.055f, 0.035f, 1f);
            pm.fillCircle(position[0] - 1, position[1] - 1, Math.max(3, r - 3));
        }
        Texture result = new Texture(pm, true);
        result.setFilter(Texture.TextureFilter.MipMapLinearLinear,
                Texture.TextureFilter.Linear);
        result.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        pm.dispose();
        return result;
    }

    private Texture createDoublingCubeTexture(int value) {
        final int size = 128;
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setColor(0.24f, 0.035f, 0.055f, 1f);
        pm.fill();
        pm.setColor(0.78f, 0.54f, 0.22f, 1f);
        pm.fillRectangle(5, 5, size - 10, 4);
        pm.fillRectangle(5, size - 9, size - 10, 4);
        pm.fillRectangle(5, 5, 4, size - 10);
        pm.fillRectangle(size - 9, 5, 4, size - 10);
        String text = String.valueOf(Math.max(1, value));
        int digitWidth = text.length() == 1 ? 48 : 36;
        int gap = text.length() == 1 ? 0 : 8;
        int total = text.length() * digitWidth + (text.length() - 1) * gap;
        int x = (size - total) / 2;
        for (int i = 0; i < text.length(); i++) {
            pm.setColor(0.96f, 0.86f, 0.58f, 1f);
            drawSevenSegmentDigit(pm, text.charAt(i) - '0', x, 30,
                    digitWidth, 68, 8);
            x += digitWidth + gap;
        }
        Texture result = new Texture(pm, true);
        result.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        result.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        pm.dispose();
        return result;
    }

    private void drawSevenSegmentDigit(Pixmap pm, int digit, int x, int y,
                                       int width, int height, int thickness) {
        final boolean[][] segments = {
                {true, true, true, true, true, true, false},
                {false, true, true, false, false, false, false},
                {true, true, false, true, true, false, true},
                {true, true, true, true, false, false, true},
                {false, true, true, false, false, true, true},
                {true, false, true, true, false, true, true},
                {true, false, true, true, true, true, true},
                {true, true, true, false, false, false, false},
                {true, true, true, true, true, true, true},
                {true, true, true, true, false, true, true}
        };
        boolean[] on = segments[MathUtils.clamp(digit, 0, 9)];
        int innerWidth = width - thickness * 2;
        int verticalHeight = height / 2 - thickness;
        if (on[0]) pm.fillRectangle(x + thickness, y + height - thickness, innerWidth, thickness);
        if (on[1]) pm.fillRectangle(x + width - thickness, y + height / 2, thickness, verticalHeight);
        if (on[2]) pm.fillRectangle(x + width - thickness, y, thickness, verticalHeight);
        if (on[3]) pm.fillRectangle(x + thickness, y, innerWidth, thickness);
        if (on[4]) pm.fillRectangle(x, y, thickness, verticalHeight);
        if (on[5]) pm.fillRectangle(x, y + height / 2, thickness, verticalHeight);
        if (on[6]) pm.fillRectangle(x + thickness, y + height / 2 - thickness / 2,
                innerWidth, thickness);
    }

    private Texture createNaturalWoodTexture(int size) {
        // Soft, irregular walnut grain. The pattern is deliberately aperiodic:
        // no sine bands, no repeated horizontal stripes, and only subtle contrast.
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        for (int y = 0; y < size; y++) {
            float v = y / (float) (size - 1);
            for (int x = 0; x < size; x++) {
                float u = x / (float) (size - 1);

                float warp = (smoothNoise(u * 2.4f, v * 2.4f) - 0.5f) * 0.75f;
                float grain = smoothNoise(
                        u * 3.2f + warp,
                        v * 13.0f + warp * 0.55f);

                float broad = smoothNoise(
                        u * 1.8f + warp * 0.35f,
                        v * 4.0f + warp * 0.25f);

                float pore = smoothNoise(
                        u * 22.0f + warp,
                        v * 30.0f - warp);

                // Mostly broad natural variation, with restrained directional grain.
                float variation = (broad - 0.5f) * 0.12f
                        + (grain - 0.5f) * 0.075f
                        + (pore - 0.5f) * 0.018f;

                float r = MathUtils.clamp(0.34f + variation * 0.90f, 0f, 1f);
                float g = MathUtils.clamp(0.16f + variation * 0.55f, 0f, 1f);
                float b = MathUtils.clamp(0.075f + variation * 0.32f, 0f, 1f);

                pm.setColor(r, g, b, 1f);
                pm.drawPixel(x, y);
            }
        }

        Texture tex = new Texture(pm, true);
        tex.setFilter(Texture.TextureFilter.MipMapLinearLinear,
                Texture.TextureFilter.Linear);
        tex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        pm.dispose();
        return tex;
    }

    /** Consistent Android-friendly material pipeline built on LibGDX DefaultShader. */
    private Material material(Texture albedo, Texture normal, float r, float g, float b,
                              float sr, float sg, float sb, float shininess) {
        Material m = new Material(ColorAttribute.createDiffuse(r, g, b, 1f));
        if (albedo != null) m.set(TextureAttribute.createDiffuse(albedo));
        if (normal != null) m.set(TextureAttribute.createNormal(normal));
        m.set(ColorAttribute.createSpecular(sr, sg, sb, 1f));
        m.set(FloatAttribute.createShininess(shininess));
        return m;
    }

    private Material boardWoodMaterial() {
        // The playfield is the hero surface: a bronze-gray damask with baked
        // vignette/AO. Frame and base retain the real walnut photo texture.
        return material(damaskTexture, null,
                1.00f, 1.00f, 1.00f,
                0.24f, 0.21f, 0.18f, 24f);
    }

    /** Polished brass hardware: clasps, corner caps and trim. */
    private Material brassMaterial() {
        Material m = new Material(ColorAttribute.createDiffuse(0.46f, 0.315f, 0.115f, 1f));
        m.set(ColorAttribute.createSpecular(0.82f, 0.62f, 0.30f, 1f));
        m.set(FloatAttribute.createShininess(110f));
        return m;
    }

    private Material glossyDarkResinMaterial() {
        Material m = material(darkCheckerTexture, darkCheckerNormalTexture,
                0.42f, 0.075f, 0.070f, 0.92f, 0.48f, 0.40f, 132f);
        applyStudioReflection(m, 0.24f, 0.10f, 0.08f);
        return m;
    }

    private Material glossyIvoryResinMaterial() {
        Material m = material(lightCheckerTexture, lightCheckerNormalTexture,
                0.96f, 0.89f, 0.77f, 0.92f, 0.78f, 0.58f, 118f);
        applyStudioReflection(m, 0.22f, 0.18f, 0.12f);
        return m;
    }

    private void applyStudioReflection(Material material, float r, float g, float b) {
        if (studioCubemap == null) return;
        material.set(new CubemapAttribute(CubemapAttribute.EnvironmentMap, studioCubemap));
        material.set(ColorAttribute.createReflection(r, g, b, 1f));
    }

    /**
     * Tiny procedural studio environment used only for subtle glossy reflections.
     * It avoids shipping another texture while giving resin and bone a controlled
     * softbox highlight instead of a flat directional-light response.
     */
    private Cubemap createStudioCubemap() {
        final int size = 32;
        Pixmap[] faces = new Pixmap[6];
        float[] faceBoost = {1.00f, 0.82f, 1.18f, 0.62f, 0.92f, 0.74f};
        for (int f = 0; f < faces.length; f++) {
            Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
            float boost = faceBoost[f];
            for (int y = 0; y < size; y++) {
                float v = y / (float)(size - 1);
                float softbox = (float)Math.exp(-Math.pow((v - 0.32f) / 0.18f, 2.0));
                for (int x = 0; x < size; x++) {
                    float u = x / (float)(size - 1);
                    float edge = 1f - Math.abs(u - 0.5f) * 0.34f;
                    float value = MathUtils.clamp((0.055f + softbox * 0.18f) * boost * edge, 0f, 1f);
                    pm.setColor(value * 0.98f, value * 0.91f, value * 0.78f, 1f);
                    pm.drawPixel(x, y);
                }
            }
            faces[f] = pm;
        }
        Cubemap cubemap = new Cubemap(new FacedCubemapData(
                faces[0], faces[1], faces[2], faces[3], faces[4], faces[5], false));
        for (Pixmap face : faces) face.dispose();
        return cubemap;
    }

    private Material wood(float r, float g, float b, float shine) {
        Material m = new Material(ColorAttribute.createDiffuse(r, g, b, 1f));
        m.set(ColorAttribute.createSpecular(
                Math.min(1f, r + 0.18f),
                Math.min(1f, g + 0.18f),
                Math.min(1f, b + 0.18f), 1f));
        m.set(FloatAttribute.createShininess(shine));
        return m;
    }

    private Material woodTextured(float r, float g, float b, float shine) {
        Material m = new Material(
                ColorAttribute.createDiffuse(r, g, b, 1f),
                TextureAttribute.createDiffuse(woodGrainTexture));
        m.set(ColorAttribute.createSpecular(
                Math.min(1f, r + 0.14f),
                Math.min(1f, g + 0.14f),
                Math.min(1f, b + 0.14f), 1f));
        m.set(FloatAttribute.createShininess(shine));
        return m;
    }


    private Texture createPieceTexture(int size, float br, float bg, float bb,
                                       float vr, float vg, float vb, long seed) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        float ox = (seed % 97) * 0.137f;
        float oy = (seed % 53) * 0.193f;
        for (int y = 0; y < size; y++) {
            float v = y / (float)(size - 1);
            for (int x = 0; x < size; x++) {
                float u = x / (float)(size - 1);
                float n1 = smoothNoise(u * 3.0f + ox, v * 3.0f + oy);
                float n2 = smoothNoise(u * 8.0f + ox * 0.7f, v * 5.0f + oy * 0.8f);
                float n3 = smoothNoise(u * 28.0f + ox, v * 28.0f + oy);
                float vein = MathUtils.clamp(
                        (n1 - 0.5f) * 0.55f + (n2 - 0.5f) * 0.22f + (n3 - 0.5f) * 0.06f,
                        -0.32f, 0.32f);
                float r = MathUtils.clamp(br + (vr - br) * (0.48f + vein), 0f, 1f);
                float g = MathUtils.clamp(bg + (vg - bg) * (0.48f + vein), 0f, 1f);
                float b = MathUtils.clamp(bb + (vb - bb) * (0.48f + vein), 0f, 1f);
                pm.setColor(r, g, b, 1f);
                pm.drawPixel(x, y);
            }
        }
        Texture tex = new Texture(pm, true);
        tex.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        tex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        pm.dispose();
        return tex;
    }

    private Material surface(float r, float g, float b) {
        Material m = new Material(ColorAttribute.createDiffuse(r, g, b, 1f));
        m.set(ColorAttribute.createSpecular(0.12f, 0.10f, 0.08f, 1f));
        m.set(FloatAttribute.createShininess(10f));
        return m;
    }

    private void buildBoard() {
        final long attrs = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates;

        // A dark furniture-like floor grounds the board in the scene instead
        // of leaving it floating against a flat black background.
        floorModel = mb.createBox(
                20.0f, 0.10f, 12.0f,
                surface(0.018f, 0.022f, 0.028f), attrs);
        models.add(new ModelInstance(floorModel, 0f, -0.84f, 0f));

        Material hudWood = woodTextured(0.42f, 0.26f, 0.14f, 50f);
        hudHeaderModel = mb.createBox(13.0f, 0.30f, 0.78f, hudWood, attrs);
        hudHeaderInsetModel = mb.createBox(11.9f, 0.07f, 0.58f,
                wood(0.20f, 0.11f, 0.05f, 30f), attrs);
        hudTurnModel = mb.createBox(3.15f, 0.10f, 0.54f,
                brassMaterial(), attrs);
        models.add(new ModelInstance(hudHeaderModel, 0f, 0.62f, -4.72f));
        models.add(new ModelInstance(hudHeaderInsetModel, 0f, 0.79f, -4.72f));
        models.add(new ModelInstance(hudTurnModel, 0f, 0.85f, -4.72f));

        // Leather travel-case body: the cabinet sits on the floor with its top
        // exactly flush with the playfield bottom, so the felt reads as an
        // inset panel instead of a floating sheet.
        baseModel = mb.createBox(
                14.8f, 0.78f, 8.6f,
                woodTextured(0.42f, 0.26f, 0.135f, 52f), attrs);
        models.add(new ModelInstance(baseModel, 0f, -0.37f, 0f));

        // Stepped base under the cabinet for a chunky case silhouette.
        Model plinthModel = mb.createBox(
                15.05f, 0.16f, 8.85f,
                wood(0.22f, 0.13f, 0.065f, 34f), attrs);
        models.add(new ModelInstance(plinthModel, 0f, -0.72f, 0f));

        // Single clean felt playfield: one solid top surface avoids layered
        // coplanar/intersecting panels that can create mobile depth artifacts.
        playingSurfaceModel = mb.createBox(
                12.55f, 0.16f, 7.45f,
                boardWoodMaterial(), attrs);
        models.add(new ModelInstance(playingSurfaceModel, 0f, 0.10f, 0f));

        // No overlay rail over the playfield. Keeping the playing surface as
        // one visible mesh prevents the repeated-line artifact seen on mobile.

        // Low leather rails with brass corner caps, like a travel case: roughly
        // half the previous height so stacked checkers rise above the walls.
        Material frameRail = woodTextured(0.46f, 0.29f, 0.155f, 58f);
        Material frameCap = woodTextured(0.52f, 0.33f, 0.175f, 64f);
        Material frameBead = wood(0.22f, 0.12f, 0.05f, 30f);
        Material frameGroove = wood(0.15f, 0.08f, 0.03f, 24f);
        Model railLong = mb.createBox(
                13.55f, 0.66f, 0.44f, frameRail, attrs);
        Model railShort = mb.createBox(
                0.44f, 0.66f, 7.48f, frameRail, attrs);
        models.add(new ModelInstance(railLong, 0f, 0.35f, -4.00f));
        models.add(new ModelInstance(railLong, 0f, 0.35f,  4.00f));
        models.add(new ModelInstance(railShort, -6.50f, 0.35f, 0f));
        models.add(new ModelInstance(railShort,  6.50f, 0.35f, 0f));

        // Dark shadow groove where the rails meet the cabinet: sells the depth.
        Model grooveLong = mb.createBox(13.55f, 0.07f, 0.50f, frameGroove, attrs);
        Model grooveShort = mb.createBox(0.50f, 0.07f, 7.48f, frameGroove, attrs);
        models.add(new ModelInstance(grooveLong, 0f, 0.055f, -4.00f));
        models.add(new ModelInstance(grooveLong, 0f, 0.055f,  4.00f));
        models.add(new ModelInstance(grooveShort, -6.50f, 0.055f, 0f));
        models.add(new ModelInstance(grooveShort,  6.50f, 0.055f, 0f));

        // Overhanging cap on top of every rail (the stitched leather lip).
        Model capLong = mb.createBox(13.85f, 0.12f, 0.66f, frameCap, attrs);
        Model capShort = mb.createBox(0.66f, 0.12f, 7.78f, frameCap, attrs);
        models.add(new ModelInstance(capLong, 0f, 0.74f, -4.00f));
        models.add(new ModelInstance(capLong, 0f, 0.74f,  4.00f));
        models.add(new ModelInstance(capShort, -6.50f, 0.74f, 0f));
        models.add(new ModelInstance(capShort,  6.50f, 0.74f, 0f));

        // Thin polished inner bead running along the inside faces.
        Model beadLong = mb.createBox(13.10f, 0.07f, 0.055f, frameBead, attrs);
        Model beadShort = mb.createBox(0.055f, 0.07f, 7.10f, frameBead, attrs);
        models.add(new ModelInstance(beadLong, 0f, 0.44f, -3.755f));
        models.add(new ModelInstance(beadLong, 0f, 0.44f,  3.755f));
        models.add(new ModelInstance(beadShort, -6.255f, 0.44f, 0f));
        models.add(new ModelInstance(beadShort,  6.255f, 0.44f, 0f));

        // Solid corner posts with polished brass caps: the case hardware.
        Model cornerPost = mb.createBox(0.60f, 0.74f, 0.60f, frameCap, attrs);
        Model cornerCap = mb.createBox(0.50f, 0.07f, 0.50f, brassMaterial(), attrs);
        models.add(new ModelInstance(cornerPost, -6.50f, 0.39f, -4.00f));
        models.add(new ModelInstance(cornerPost,  6.50f, 0.39f, -4.00f));
        models.add(new ModelInstance(cornerPost, -6.50f, 0.39f,  4.00f));
        models.add(new ModelInstance(cornerPost,  6.50f, 0.39f,  4.00f));
        models.add(new ModelInstance(cornerCap, -6.50f, 0.78f, -4.00f));
        models.add(new ModelInstance(cornerCap,  6.50f, 0.78f, -4.00f));
        models.add(new ModelInstance(cornerCap, -6.50f, 0.78f,  4.00f));
        models.add(new ModelInstance(cornerCap,  6.50f, 0.78f,  4.00f));

        // Subtle central divider shoulders, keeping the bar visually integrated.
        Model barShoulder = mb.createBox(
                0.92f, 0.10f, 7.42f,
                wood(0.20f, 0.10f, 0.045f, 26f), attrs);
        models.add(new ModelInstance(barShoulder, 0f, 0.28f, 0f));

        // Central bar with a subtle raised center strip.
        barModel = mb.createBox(
                0.84f, 0.30f, 7.34f,
                wood(0.22f, 0.13f, 0.065f, 40f), attrs);
        models.add(new ModelInstance(barModel, 0f, 0.27f, 0f));

        Model barHighlight = mb.createBox(
                0.08f, 0.03f, 7.10f,
                brassMaterial(), attrs);
        models.add(new ModelInstance(barHighlight, 0f, 0.43f, 0f));

        Material barCapMaterial = wood(0.30f, 0.16f, 0.06f, 40f);
        Model barCap = mb.createBox(0.24f, 0.035f, 6.95f, barCapMaterial, attrs);
        models.add(new ModelInstance(barCap, 0f, 0.47f, 0f));

        // Burgundy backing panels remain, but the separate gold bars are removed:
        // those coplanar strips were the source of the repeated horizontal lines
        // across the playfield. The triangles themselves supply the clean graphic.
        pointRailModel = mb.createBox(0.82f, 0.022f, 3.18f,
                new Material(
                        ColorAttribute.createDiffuse(0.24f, 0.045f, 0.065f, 1f),
                        ColorAttribute.createSpecular(0.36f, 0.11f, 0.10f, 1f),
                        FloatAttribute.createShininess(68f)), attrs);
        for (int i = 0; i < 12; i++) {
            addPointRailInstance(XS[i], -2.02f);
            addPointRailInstance(XS[i],  2.02f);
        }

        // Points on the damask: deep chocolate brown and ivory. The dark points sit
        // clearly below the playfield value so every triangle reads instantly,
        // while the raised top cap keeps a lacquered highlight on mobile light.
        darkPointModel = createPointModel(
                new Material(
                        ColorAttribute.createDiffuse(0.26f, 0.135f, 0.055f, 1f),
                        ColorAttribute.createSpecular(0.34f, 0.21f, 0.11f, 1f),
                        FloatAttribute.createShininess(48f)),
                new Material(
                        ColorAttribute.createDiffuse(0.18f, 0.09f, 0.035f, 1f),
                        ColorAttribute.createSpecular(0.44f, 0.27f, 0.13f, 1f),
                        FloatAttribute.createShininess(62f)), attrs);
        lightPointModel = createPointModel(
                new Material(
                        ColorAttribute.createDiffuse(0.88f, 0.82f, 0.66f, 1f),
                        ColorAttribute.createSpecular(0.45f, 0.40f, 0.30f, 1f),
                        FloatAttribute.createShininess(46f)),
                new Material(
                        ColorAttribute.createDiffuse(0.78f, 0.70f, 0.55f, 1f),
                        ColorAttribute.createSpecular(0.60f, 0.52f, 0.36f, 1f),
                        FloatAttribute.createShininess(64f)), attrs);

        // Thin dark bed beneath every point. It is deliberately larger than
        // the inlay and only a fraction of the height, creating a natural
        // recessed/AO edge instead of a flat painted triangle.
        pointShadowModel = createPointModel(
                new Material(
                        ColorAttribute.createDiffuse(0.050f, 0.050f, 0.055f, 1f),
                        ColorAttribute.createSpecular(0.10f, 0.10f, 0.10f, 1f),
                        FloatAttribute.createShininess(24f)),
                new Material(
                        ColorAttribute.createDiffuse(0.028f, 0.028f, 0.030f, 1f),
                        ColorAttribute.createSpecular(0.07f, 0.07f, 0.07f, 1f),
                        FloatAttribute.createShininess(18f)), attrs);

        for (int i = 0; i < 12; i++) {
            ModelInstance bottomShadow = new ModelInstance(
                    pointShadowModel, XS[i], 0.182f, -2.02f);
            bottomShadow.transform.rotate(Vector3.Y, 180f);
            bottomShadow.transform.scale(1.06f, 0.34f, 1.06f);
            models.add(bottomShadow);

            ModelInstance topShadow = new ModelInstance(
                    pointShadowModel, XS[i], 0.182f, 2.02f);
            topShadow.transform.scale(1.06f, 0.34f, 1.06f);
            models.add(topShadow);

            ModelInstance bottomPoint = new ModelInstance(
                    (i % 2 == 0) ? darkPointModel : lightPointModel,
                    XS[i], 0.18f, -2.02f);
            bottomPoint.transform.rotate(Vector3.Y, 180f);
            models.add(bottomPoint);

            ModelInstance topPoint = new ModelInstance(
                    (i % 2 == 0) ? lightPointModel : darkPointModel,
                    XS[i], 0.18f, 2.02f);
            models.add(topPoint);
        }

        // Shared checker meshes. High radial resolution keeps the circular
        // silhouette clean on modern phone displays while remaining lightweight.
        // Dark: glossy chocolate lacquer with a cream inlaid ring (like the
        // store-bought sets). Light: polished ivory with a tan inlaid ring.
        darkChecker = createLuxuryCheckerModel(darkCheckerTexture,
                new Material(
                        ColorAttribute.createDiffuse(0.15f, 0.035f, 0.045f, 1f),
                        ColorAttribute.createSpecular(0.68f, 0.25f, 0.22f, 1f),
                        FloatAttribute.createShininess(150f)),
                new Material(
                        ColorAttribute.createDiffuse(0.72f, 0.54f, 0.28f, 1f),
                        ColorAttribute.createSpecular(0.78f, 0.58f, 0.32f, 1f),
                        FloatAttribute.createShininess(130f)),
                new Material(
                        ColorAttribute.createDiffuse(0.22f, 0.045f, 0.055f, 1f),
                        ColorAttribute.createSpecular(0.78f, 0.30f, 0.25f, 1f),
                        FloatAttribute.createShininess(165f)),
                attrs);

        lightChecker = createLuxuryCheckerModel(lightCheckerTexture,
                new Material(
                        ColorAttribute.createDiffuse(0.72f, 0.66f, 0.54f, 1f),
                        ColorAttribute.createSpecular(0.76f, 0.67f, 0.49f, 1f),
                        FloatAttribute.createShininess(118f)),
                new Material(
                        ColorAttribute.createDiffuse(0.60f, 0.45f, 0.25f, 1f),
                        ColorAttribute.createSpecular(0.70f, 0.53f, 0.30f, 1f),
                        FloatAttribute.createShininess(130f)),
                new Material(
                        ColorAttribute.createDiffuse(0.82f, 0.75f, 0.60f, 1f),
                        ColorAttribute.createSpecular(0.78f, 0.69f, 0.50f, 1f),
                        FloatAttribute.createShininess(120f)),
                attrs);

        // Small bone cube whose six faces already contain large, high-contrast
        // pips. The visible top face is rotated to the rolled value at settle.
        diceModel = createPippedDieModel(0.62f, attrs);

        // A radial alpha texture replaces the old hard black cylinder. It fades
        // from 0.38 opacity at the contact point to zero at the soft edge.
        checkerShadowModel = mb.createCylinder(
                0.50f, 0.008f, 0.50f, 48,
                new Material(
                        TextureAttribute.createDiffuse(checkerShadowTexture),
                        ColorAttribute.createDiffuse(1f, 1f, 1f, 1f),
                        new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 1f)),
                attrs);

        // Small gold marker used for legal-move hints (no center instance:
        // the dice tray occupies the middle of the bar).
        accentModel = mb.createCylinder(
                0.16f, 0.035f, 0.16f, 32,
                wood(0.78f, 0.50f, 0.17f, 52f), attrs);

        // Premium dice tray: a shallow felt-lined well makes the dice feel
        // physically seated on the board instead of floating above it.
        diceTrayModel = mb.createBox(
                2.55f, 0.07f, 1.55f,
                wood(0.20f, 0.115f, 0.055f, 40f), attrs);
        models.add(new ModelInstance(diceTrayModel, 0f, 0.48f, 0f));

        Model diceTrayInset = mb.createBox(
                2.30f, 0.055f, 1.25f,
                surface(0.045f, 0.030f, 0.018f), attrs);
        models.add(new ModelInstance(diceTrayInset, 0f, 0.54f, 0f));

        // Raised brass trim around the dice well gives the center a crafted,
        // case-like finish while keeping the dice area visually clean.
        Material trayTrim = brassMaterial();
        Model trayTrimX = mb.createBox(2.38f, 0.045f, 0.06f, trayTrim, attrs);
        Model trayTrimZ = mb.createBox(0.06f, 0.045f, 1.25f, trayTrim, attrs);
        models.add(new ModelInstance(trayTrimX, 0f, 0.54f, -0.64f));
        models.add(new ModelInstance(trayTrimX, 0f, 0.54f,  0.64f));
        models.add(new ModelInstance(trayTrimZ, -1.19f, 0.54f, 0f));
        models.add(new ModelInstance(trayTrimZ,  1.19f, 0.54f, 0f));

        // Standing doubling cube in the left home corner. Its top is a real
        // texture, not UI text, so it catches the same light as the board.
        doublingCubeModel = createDoublingCubeModel(0.58f, attrs);
        doublingCubeInstance = new ModelInstance(
                doublingCubeModel, -5.15f, 0.54f, -3.18f);
        models.add(doublingCubeInstance);

        // Right-side borne-off storage: two routed felt wells split by a brass
        // spine — bottom well for White, top well for Black. The wells stay
        // part of the board shell so the playfield keeps its clean silhouette.
        sideTrayModel = mb.createBox(
                0.72f, 0.16f, 7.70f,
                wood(0.22f, 0.13f, 0.06f, 38f), attrs);
        sideTrayInsetModel = mb.createBox(
                0.56f, 0.07f, 3.60f,
                surface(0.055f, 0.036f, 0.022f), attrs);
        models.add(new ModelInstance(sideTrayModel,  7.02f, 0.10f, 0f));
        models.add(new ModelInstance(sideTrayInsetModel,  7.02f, 0.19f, -1.92f));
        models.add(new ModelInstance(sideTrayInsetModel,  7.02f, 0.19f,  1.92f));

        // Brass spine dividing the White (bottom) and Black (top) wells.
        Model offDivider = mb.createBox(0.72f, 0.16f, 0.24f,
                wood(0.22f, 0.13f, 0.06f, 38f), attrs);
        models.add(new ModelInstance(offDivider, 7.02f, 0.10f, 0f));
        Model offDividerBrass = mb.createBox(0.60f, 0.04f, 0.20f,
                brassMaterial(), attrs);
        models.add(new ModelInstance(offDividerBrass, 7.02f, 0.20f, 0f));

        // Thin leather lips make the wells read as routed recesses.
        Material trayLip = woodTextured(0.44f, 0.27f, 0.145f, 48f);
        Model trayLipX = mb.createBox(0.06f, 0.075f, 7.42f, trayLip, attrs);
        models.add(new ModelInstance(trayLipX,  6.63f, 0.22f, 0f));
        models.add(new ModelInstance(trayLipX,  7.34f, 0.22f, 0f));

        // Decorative medallions on each half of the board.
        medallionModel = mb.createCylinder(
                0.43f, 0.035f, 0.43f, 48,
                woodTextured(0.56f, 0.45f, 0.34f, 50f), attrs);
        medallionRingModel = mb.createCylinder(
                0.31f, 0.045f, 0.31f, 48,
                wood(0.72f, 0.46f, 0.15f, 58f), attrs);
        // Decorative hardware omitted from the gameplay surface.

        // Center hinge plates and brass fasteners make the bar feel like a real case seam.
        hingePlateModel = mb.createBox(
                0.48f, 0.055f, 1.35f,
                wood(0.58f, 0.34f, 0.10f, 62f), attrs);
        // Decorative hardware omitted from the gameplay surface.

        Model hingeScrew = mb.createCylinder(
                0.075f, 0.035f, 0.075f, 20,
                wood(0.76f, 0.52f, 0.20f, 70f), attrs);
        // Decorative hardware omitted from the gameplay surface.

        // Four small brass-like fasteners on the board corners.
        screwModel = mb.createCylinder(
                0.105f, 0.045f, 0.105f, 24,
                wood(0.78f, 0.50f, 0.17f, 58f), attrs);
        // Decorative hardware omitted from the gameplay surface.

        rebuildGameObjects();
    }

    private void addPointRailInstance(float x, float z) {
        models.add(new ModelInstance(pointRailModel, x, 0.172f, z));
    }

    private Material dieFaceMaterial(int face) {
        Material m = new Material(
                TextureAttribute.createDiffuse(dieFaceTextures[face - 1]),
                ColorAttribute.createSpecular(0.42f, 0.36f, 0.26f, 1f),
                FloatAttribute.createShininess(76f));
        applyStudioReflection(m, 0.10f, 0.075f, 0.045f);
        return m;
    }

    private Model createPippedDieModel(float size, long attrs) {
        float s = size * 0.5f;
        mb.begin();

        // Opposite pairs follow a normal die: 1/6, 2/5, 3/4. The local +Y
        // face starts at one, and setDieFace rotates the desired value upward.
        addCubeFace(mb.part("die_top", GL20.GL_TRIANGLES, attrs, dieFaceMaterial(1)),
                new Vector3(-s, s, -s), new Vector3(-s, s, s),
                new Vector3(s, s, s), new Vector3(s, s, -s), Vector3.Y);
        addCubeFace(mb.part("die_bottom", GL20.GL_TRIANGLES, attrs, dieFaceMaterial(6)),
                new Vector3(-s, -s, -s), new Vector3(s, -s, -s),
                new Vector3(s, -s, s), new Vector3(-s, -s, s), Vector3.Y.cpy().scl(-1f));
        addCubeFace(mb.part("die_front", GL20.GL_TRIANGLES, attrs, dieFaceMaterial(2)),
                new Vector3(-s, -s, s), new Vector3(s, -s, s),
                new Vector3(s, s, s), new Vector3(-s, s, s), Vector3.Z);
        addCubeFace(mb.part("die_back", GL20.GL_TRIANGLES, attrs, dieFaceMaterial(5)),
                new Vector3(-s, -s, -s), new Vector3(-s, s, -s),
                new Vector3(s, s, -s), new Vector3(s, -s, -s), Vector3.Z.cpy().scl(-1f));
        addCubeFace(mb.part("die_right", GL20.GL_TRIANGLES, attrs, dieFaceMaterial(3)),
                new Vector3(s, -s, -s), new Vector3(s, s, -s),
                new Vector3(s, s, s), new Vector3(s, -s, s), Vector3.X);
        addCubeFace(mb.part("die_left", GL20.GL_TRIANGLES, attrs, dieFaceMaterial(4)),
                new Vector3(-s, -s, s), new Vector3(-s, s, s),
                new Vector3(-s, s, -s), new Vector3(-s, -s, -s), Vector3.X.cpy().scl(-1f));
        return mb.end();
    }

    private Model createDoublingCubeModel(float size, long attrs) {
        float s = size * 0.5f;
        Material side = new Material(
                ColorAttribute.createDiffuse(0.28f, 0.045f, 0.065f, 1f),
                ColorAttribute.createSpecular(0.60f, 0.22f, 0.18f, 1f),
                FloatAttribute.createShininess(104f));
        applyStudioReflection(side, 0.15f, 0.035f, 0.035f);
        doublingCubeTopMaterial = new Material(
                TextureAttribute.createDiffuse(doublingCubeTexture),
                ColorAttribute.createSpecular(0.68f, 0.46f, 0.25f, 1f),
                FloatAttribute.createShininess(96f));
        applyStudioReflection(doublingCubeTopMaterial, 0.12f, 0.05f, 0.03f);

        mb.begin();
        addCubeFace(mb.part("cube_top", GL20.GL_TRIANGLES, attrs, doublingCubeTopMaterial),
                new Vector3(-s, s, -s), new Vector3(-s, s, s),
                new Vector3(s, s, s), new Vector3(s, s, -s), Vector3.Y);
        addCubeFace(mb.part("cube_bottom", GL20.GL_TRIANGLES, attrs, side),
                new Vector3(-s, -s, -s), new Vector3(s, -s, -s),
                new Vector3(s, -s, s), new Vector3(-s, -s, s), Vector3.Y.cpy().scl(-1f));
        addCubeFace(mb.part("cube_front", GL20.GL_TRIANGLES, attrs, side),
                new Vector3(-s, -s, s), new Vector3(s, -s, s),
                new Vector3(s, s, s), new Vector3(-s, s, s), Vector3.Z);
        addCubeFace(mb.part("cube_back", GL20.GL_TRIANGLES, attrs, side),
                new Vector3(-s, -s, -s), new Vector3(-s, s, -s),
                new Vector3(s, s, -s), new Vector3(s, -s, -s), Vector3.Z.cpy().scl(-1f));
        addCubeFace(mb.part("cube_right", GL20.GL_TRIANGLES, attrs, side),
                new Vector3(s, -s, -s), new Vector3(s, s, -s),
                new Vector3(s, s, s), new Vector3(s, -s, s), Vector3.X);
        addCubeFace(mb.part("cube_left", GL20.GL_TRIANGLES, attrs, side),
                new Vector3(-s, -s, s), new Vector3(-s, s, s),
                new Vector3(-s, s, -s), new Vector3(-s, -s, -s), Vector3.X.cpy().scl(-1f));
        return mb.end();
    }

    private void addCubeFace(MeshPartBuilder part, Vector3 v00, Vector3 v01,
                             Vector3 v11, Vector3 v10, Vector3 normal) {
        VertexInfo a = new VertexInfo().set(v00, normal, null, new Vector2(0f, 0f));
        VertexInfo b = new VertexInfo().set(v01, normal, null, new Vector2(0f, 1f));
        VertexInfo c = new VertexInfo().set(v11, normal, null, new Vector2(1f, 1f));
        VertexInfo d = new VertexInfo().set(v10, normal, null, new Vector2(1f, 0f));
        part.triangle(a, b, c);
        part.triangle(a, c, d);
    }

    private void setDieFace(ModelInstance die, int face) {
        if (die == null) return;
        int value = MathUtils.clamp(face, 1, 6);
        // The model starts with one on +Y. These rotations move the requested
        // numbered face to +Y while keeping the cube perfectly upright.
        if (value == 6) die.transform.rotate(Vector3.X, 180f);
        else if (value == 2) die.transform.rotate(Vector3.X, -90f);
        else if (value == 5) die.transform.rotate(Vector3.X, 90f);
        else if (value == 3) die.transform.rotate(Vector3.Z, 90f);
        else if (value == 4) die.transform.rotate(Vector3.Z, -90f);
    }

    private void triQuad(MeshPartBuilder p, Vector3 a, Vector3 b, Vector3 c, Vector3 d) {
        p.triangle(a, b, c);
        p.triangle(a, c, d);
    }

    private Model createLuxuryCheckerModel(Texture topTexture, Material material, Material detailMaterial, Material rimMaterial, long attrs) {
        // Fresh turned-checker geometry: a real beveled disc with a recessed
        // top inlay, clean side wall and no paper-thin stacked-card silhouette.
        // Controlled studio reflection on the curved bands creates a Fresnel-like
        // edge response without requiring a custom shader on mobile.
        applyStudioReflection(material, 0.18f, 0.07f, 0.055f);
        applyStudioReflection(detailMaterial, 0.22f, 0.09f, 0.07f);
        applyStudioReflection(rimMaterial, 0.30f, 0.13f, 0.10f);

        mb.begin();
        MeshPartBuilder p = mb.part("checker", GL20.GL_TRIANGLES, attrs, material);

        final int segments = 48;
        final float radius = 0.38f;
        final float bevelRadius = 0.040f;
        final float half = 0.085f;
        final float bevelY = 0.030f;

        for (int i = 0; i < segments; i++) {
            float a0 = MathUtils.PI2 * i / segments;
            float a1 = MathUtils.PI2 * (i + 1) / segments;
            float x0 = MathUtils.cos(a0) * radius;
            float z0 = MathUtils.sin(a0) * radius;
            float x1 = MathUtils.cos(a1) * radius;
            float z1 = MathUtils.sin(a1) * radius;

            float bx0 = MathUtils.cos(a0) * (radius - bevelRadius);
            float bz0 = MathUtils.sin(a0) * (radius - bevelRadius);
            float bx1 = MathUtils.cos(a1) * (radius - bevelRadius);
            float bz1 = MathUtils.sin(a1) * (radius - bevelRadius);

            Vector3 top0 = new Vector3(bx0, half + bevelY, bz0);
            Vector3 top1 = new Vector3(bx1, half + bevelY, bz1);
            Vector3 topC0 = new Vector3(x0, half, z0);
            Vector3 topC1 = new Vector3(x1, half, z1);
            Vector3 bot0 = new Vector3(bx0, -half - bevelY, bz0);
            Vector3 bot1 = new Vector3(bx1, -half - bevelY, bz1);
            Vector3 botC0 = new Vector3(x0, -half, z0);
            Vector3 botC1 = new Vector3(x1, -half, z1);

            p.triangle(top0, topC0, topC1);
            p.triangle(top0, topC1, top1);
            p.triangle(bot0, bot1, botC1);
            p.triangle(bot0, botC1, botC0);
            p.triangle(new Vector3(0f, -half - bevelY, 0f), bot0, bot1);
            p.triangle(topC0, botC0, botC1);
            p.triangle(topC0, botC1, topC1);
            p.triangle(top0, top1, bot1);
            p.triangle(top0, bot1, bot0);
        }

        Material topMaterial = new Material(
                TextureAttribute.createDiffuse(topTexture),
                TextureAttribute.createNormal(
                        topTexture == lightCheckerTexture
                                ? lightCheckerNormalTexture : darkCheckerNormalTexture),
                ColorAttribute.createSpecular(0.68f, 0.60f, 0.48f, 1f),
                FloatAttribute.createShininess(92f));
        applyStudioReflection(topMaterial, 0.14f, 0.10f, 0.075f);
        MeshPartBuilder top = mb.part("checker_top", GL20.GL_TRIANGLES, attrs, topMaterial);
        final float topRadius = radius - bevelRadius;
        final Vector3 topCenter = new Vector3(0f, half + bevelY + 0.001f, 0f);
        for (int i = 0; i < segments; i++) {
            float a0 = MathUtils.PI2 * i / segments;
            float a1 = MathUtils.PI2 * (i + 1) / segments;
            Vector3 v0 = new Vector3(MathUtils.cos(a0) * topRadius, topCenter.y, MathUtils.sin(a0) * topRadius);
            Vector3 v1 = new Vector3(MathUtils.cos(a1) * topRadius, topCenter.y, MathUtils.sin(a1) * topRadius);
            VertexInfo c = new VertexInfo().set(topCenter, Vector3.Y, null, new Vector2(0.5f, 0.5f));
            VertexInfo a = new VertexInfo().set(v1, Vector3.Y, null,
                    new Vector2(0.5f + v1.x / (2f * topRadius), 0.5f + v1.z / (2f * topRadius)));
            VertexInfo b = new VertexInfo().set(v0, Vector3.Y, null,
                    new Vector2(0.5f + v0.x / (2f * topRadius), 0.5f + v0.z / (2f * topRadius)));
            top.triangle(c, a, b);
        }

        // Classic turned-checker detail: a recessed ring groove plus a small
        // center hub, built with explicit up-normals so mobile GPUs shade the
        // top face cleanly from the overhead camera.
        MeshPartBuilder ring = mb.part("checker_ring", GL20.GL_TRIANGLES, attrs, detailMaterial);
        final float ringOuter = 0.270f;
        final float ringInner = 0.214f;
        final float ringY = topCenter.y + 0.0035f;
        for (int i = 0; i < segments; i++) {
            float a0 = MathUtils.PI2 * i / segments;
            float a1 = MathUtils.PI2 * (i + 1) / segments;
            Vector3 o0 = new Vector3(MathUtils.cos(a0) * ringOuter, ringY, MathUtils.sin(a0) * ringOuter);
            Vector3 o1 = new Vector3(MathUtils.cos(a1) * ringOuter, ringY, MathUtils.sin(a1) * ringOuter);
            Vector3 in0 = new Vector3(MathUtils.cos(a0) * ringInner, ringY, MathUtils.sin(a0) * ringInner);
            Vector3 in1 = new Vector3(MathUtils.cos(a1) * ringInner, ringY, MathUtils.sin(a1) * ringInner);
            VertexInfo vo0 = new VertexInfo().set(o0, Vector3.Y, null, new Vector2(0.5f, 0.5f));
            VertexInfo vo1 = new VertexInfo().set(o1, Vector3.Y, null, new Vector2(0.5f, 0.5f));
            VertexInfo vi0 = new VertexInfo().set(in0, Vector3.Y, null, new Vector2(0.5f, 0.5f));
            VertexInfo vi1 = new VertexInfo().set(in1, Vector3.Y, null, new Vector2(0.5f, 0.5f));
            ring.triangle(vo0, vi0, vo1);
            ring.triangle(vo1, vi0, vi1);
        }

        // Thin pinstripe near the rim, like the inlaid store-bought sets.
        MeshPartBuilder pinstripe = mb.part("checker_pinstripe", GL20.GL_TRIANGLES, attrs, detailMaterial);
        final float pinOuter = 0.315f;
        final float pinInner = 0.302f;
        for (int i = 0; i < segments; i++) {
            float a0 = MathUtils.PI2 * i / segments;
            float a1 = MathUtils.PI2 * (i + 1) / segments;
            Vector3 o0 = new Vector3(MathUtils.cos(a0) * pinOuter, ringY, MathUtils.sin(a0) * pinOuter);
            Vector3 o1 = new Vector3(MathUtils.cos(a1) * pinOuter, ringY, MathUtils.sin(a1) * pinOuter);
            Vector3 in0 = new Vector3(MathUtils.cos(a0) * pinInner, ringY, MathUtils.sin(a0) * pinInner);
            Vector3 in1 = new Vector3(MathUtils.cos(a1) * pinInner, ringY, MathUtils.sin(a1) * pinInner);
            VertexInfo vo0 = new VertexInfo().set(o0, Vector3.Y, null, new Vector2(0.5f, 0.5f));
            VertexInfo vo1 = new VertexInfo().set(o1, Vector3.Y, null, new Vector2(0.5f, 0.5f));
            VertexInfo vi0 = new VertexInfo().set(in0, Vector3.Y, null, new Vector2(0.5f, 0.5f));
            VertexInfo vi1 = new VertexInfo().set(in1, Vector3.Y, null, new Vector2(0.5f, 0.5f));
            pinstripe.triangle(vo0, vi0, vo1);
            pinstripe.triangle(vo1, vi0, vi1);
        }

        MeshPartBuilder hub = mb.part("checker_hub", GL20.GL_TRIANGLES, attrs, rimMaterial);
        final float hubRadius = 0.074f;
        final Vector3 hubCenter = new Vector3(0f, ringY, 0f);
        for (int i = 0; i < 24; i++) {
            float a0 = MathUtils.PI2 * i / 24;
            float a1 = MathUtils.PI2 * (i + 1) / 24;
            Vector3 v0 = new Vector3(MathUtils.cos(a0) * hubRadius, ringY, MathUtils.sin(a0) * hubRadius);
            Vector3 v1 = new Vector3(MathUtils.cos(a1) * hubRadius, ringY, MathUtils.sin(a1) * hubRadius);
            VertexInfo c = new VertexInfo().set(hubCenter, Vector3.Y, null, new Vector2(0.5f, 0.5f));
            VertexInfo a = new VertexInfo().set(v1, Vector3.Y, null, new Vector2(0.5f, 0.5f));
            VertexInfo b = new VertexInfo().set(v0, Vector3.Y, null, new Vector2(0.5f, 0.5f));
            hub.triangle(c, a, b);
        }
        return mb.end();
    }

    private void addCylinderBand(MeshPartBuilder p, float radius, float y0, float y1, int segments) {
        for (int i = 0; i < segments; i++) {
            float a0 = MathUtils.PI2 * i / segments;
            float a1 = MathUtils.PI2 * (i + 1) / segments;
            Vector3 a = new Vector3(MathUtils.cos(a0) * radius, y0, MathUtils.sin(a0) * radius);
            Vector3 b = new Vector3(MathUtils.cos(a1) * radius, y0, MathUtils.sin(a1) * radius);
            Vector3 c = new Vector3(MathUtils.cos(a1) * radius, y1, MathUtils.sin(a1) * radius);
            Vector3 d = new Vector3(MathUtils.cos(a0) * radius, y1, MathUtils.sin(a0) * radius);
            triQuad(p, a, b, c, d);
        }
    }

    private void addDisc(MeshPartBuilder p, float radius, float y, int segments) {
        Vector3 center = new Vector3(0f, y, 0f);
        for (int i = 0; i < segments; i++) {
            float a0 = MathUtils.PI2 * i / segments;
            float a1 = MathUtils.PI2 * (i + 1) / segments;
            Vector3 v0 = new Vector3(MathUtils.cos(a0) * radius, y, MathUtils.sin(a0) * radius);
            Vector3 v1 = new Vector3(MathUtils.cos(a1) * radius, y, MathUtils.sin(a1) * radius);
            p.triangle(center, v1, v0);
        }
    }

    private void addRing(MeshPartBuilder p, float outerRadius, float innerRadius, float y, int segments) {
        for (int i = 0; i < segments; i++) {
            float a0 = MathUtils.PI2 * i / segments;
            float a1 = MathUtils.PI2 * (i + 1) / segments;
            Vector3 o0 = new Vector3(MathUtils.cos(a0) * outerRadius, y, MathUtils.sin(a0) * outerRadius);
            Vector3 o1 = new Vector3(MathUtils.cos(a1) * outerRadius, y, MathUtils.sin(a1) * outerRadius);
            Vector3 in0 = new Vector3(MathUtils.cos(a0) * innerRadius, y, MathUtils.sin(a0) * innerRadius);
            Vector3 in1 = new Vector3(MathUtils.cos(a1) * innerRadius, y, MathUtils.sin(a1) * innerRadius);
            p.triangle(o0, o1, in0);
            p.triangle(o1, in1, in0);
        }
    }

    private Texture createBoardArtworkTexture() {
        final int W = 1024, H = 512;
        Pixmap p = new Pixmap(W, H, Pixmap.Format.RGBA8888);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float wave = (float)Math.sin(x * 0.035f + Math.sin(y * 0.018f) * 2.2f);
                float fine = (float)Math.sin(x * 0.17f + y * 0.012f);
                float n = wave * 0.018f + fine * 0.008f;
                p.setColor(new Color(0.29f + n, 0.145f + n * 0.65f, 0.075f + n * 0.40f, 1f));
                p.drawPixel(x, y);
            }
        }
        p.setColor(new Color(0.32f, 0.18f, 0.10f, 1f));
        p.fillRectangle(52, 24, 430, 464);
        p.fillRectangle(542, 24, 430, 464);

        float[] light = {0.79f, 0.58f, 0.32f};
        float[] dark = {0.34f, 0.075f, 0.045f};
        int left = 62, top = 36, bottom = 476, center = 256;
        for (int side = 0; side < 2; side++) {
            int sx = side == 0 ? left : 542;
            for (int i = 0; i < 6; i++) {
                int x0 = sx + i * 70, x1 = x0 + 70;
                drawTri(p, x0, top, x1, top, (i % 2 == 0) ? light : dark, center - 8);
                drawTri(p, x0, bottom, x1, bottom, (i % 2 == 0) ? dark : light, center + 8);
            }
        }
        drawMedallion(p, 256, 256);
        drawMedallion(p, 768, 256);
        Texture result = new Texture(p, true);
        p.dispose();
        return result;
    }

    private void drawTri(Pixmap p, int x0, int y0, int x1, int y1, float[] c, int tipY) {
        p.setColor(c[0], c[1], c[2], 1f);
        int xm = (x0 + x1) / 2;
        int edge = Math.min(y0, y1);
        p.fillTriangle(x0, edge, x1, edge, xm, tipY);
    }

    private void drawMedallion(Pixmap p, int cx, int cy) {
        p.setColor(0.78f, 0.57f, 0.31f, 1f);
        p.fillCircle(cx, cy, 58);
        p.setColor(0.28f, 0.14f, 0.07f, 1f);
        p.fillCircle(cx, cy, 50);
        p.setColor(0.82f, 0.63f, 0.36f, 1f);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            int x = (int)(cx + 43 * Math.cos(a));
            int y = (int)(cy + 43 * Math.sin(a));
            p.fillTriangle(cx, cy, x - 7, y - 7, x + 7, y + 7);
        }
        p.fillCircle(cx, cy, 10);
    }

    private Model createPointModel(Material material, Material detailMaterial, long attrs) {
        mb.begin();
        MeshPartBuilder p = mb.part("point_body", GL20.GL_TRIANGLES, attrs, material);

        final float w = 0.335f;
        final float y0 = 0f;
        final float y1 = 0.115f;
        final float zBase = 1.45f;
        final float zTip = -1.62f;

        Vector3 a0 = new Vector3(-w, y0, zBase);
        Vector3 b0 = new Vector3( w, y0, zBase);
        Vector3 c0 = new Vector3(0f, y0, zTip);
        Vector3 a1 = new Vector3(-w, y1, zBase);
        Vector3 b1 = new Vector3( w, y1, zBase);
        Vector3 c1 = new Vector3(0f, y1, zTip);

        // Solid raised inlay body.
        p.triangle(c0, b0, a0);
        p.triangle(a0, b0, b1);
        p.triangle(a0, b1, a1);
        p.triangle(b0, c0, c1);
        p.triangle(b0, c1, b1);
        p.triangle(c0, a0, a1);
        p.triangle(c0, a1, c1);

        // A smaller inset cap leaves a narrow polished border around the
        // point. This gives the triangular inlay a routed, crafted edge
        // instead of a single flat polygon.
        final float insetW = 0.282f;
        final float insetBase = 1.33f;
        final float insetTip = -1.47f;
        final float insetY = y1 + 0.010f;

        VertexInfo ia = new VertexInfo().set(
                new Vector3(-insetW, insetY, insetBase), Vector3.Y, null,
                new Vector2(0.0f, 0.0f));
        VertexInfo ib = new VertexInfo().set(
                new Vector3( insetW, insetY, insetBase), Vector3.Y, null,
                new Vector2(1.0f, 0.0f));
        VertexInfo ic = new VertexInfo().set(
                new Vector3(0f, insetY, insetTip), Vector3.Y, null,
                new Vector2(0.5f, 1.0f));
        MeshPartBuilder inset = mb.part("point_inlay", GL20.GL_TRIANGLES, attrs, detailMaterial);
        inset.triangle(ia, ib, ic);

        return mb.end();
    }
    private void addStack(Model model, float x, float z, int count) {
        for (int i = 0; i < count; i++) {
            float offset = i * 0.43f;
            float signed = z > 0f ? -offset : offset;
            models.add(new ModelInstance(model, x, 0.78f + i * 0.425f, z + signed));
        }
    }

    private void updateCamera() {
        float az = MathUtils.degreesToRadians * cameraAzimuth;
        float el = MathUtils.degreesToRadians * cameraElevation;
        float cosEl = MathUtils.cos(el);

        camera.position.set(
                cameraTarget.x + cameraDistance * cosEl * MathUtils.sin(az),
                cameraTarget.y + cameraDistance * MathUtils.sin(el),
                cameraTarget.z + cameraDistance * cosEl * MathUtils.cos(az));

        camera.lookAt(cameraTarget);
        camera.up.set(Vector3.Y);
        camera.fieldOfView = 30f;
        camera.near = 0.1f;
        camera.far = 100f;
        camera.update();
    }

    @Override
    public void render(float delta) {
        if (statusTimer > 0f) statusTimer -= delta;
        if (diceRollTime > 0f) {
            diceRollElapsed += delta;
            diceRollTime = Math.max(0f, diceRollTime - delta);
            float progress = MathUtils.clamp(diceRollTime / 0.95f, 0f, 1f);
            float spin = (1220f + diceThrowStrength * 2.8f) * delta * (0.45f + progress * 0.75f);
            float settle = MathUtils.clamp(1f - diceRollTime / 0.95f, 0f, 1f);
            float liftScale = MathUtils.sin(MathUtils.PI * settle);
            float lateral = MathUtils.sin(diceRollElapsed * 11f) * (0.035f + diceThrowStrength * 0.00012f);
            if (dieInstanceA != null) {
                dieInstanceA.transform.rotate(Vector3.X, spin).rotate(Vector3.Y, spin * 0.65f);
                dieInstanceA.transform.rotate(Vector3.Z, MathUtils.sin(diceRollElapsed * 17f) * 0.9f);
                float liftA = liftScale * (0.34f + diceThrowStrength * 0.00032f);
                float offsetA = lateral;
                dieInstanceA.transform.translate(offsetA, liftA - lastDiceLiftA, -offsetA * 0.45f);
                lastDiceLiftA = liftA;
            }
            if (dieInstanceB != null) {
                dieInstanceB.transform.rotate(Vector3.X, -spin * 0.85f).rotate(Vector3.Z, spin);
                dieInstanceB.transform.rotate(Vector3.Y, MathUtils.cos(diceRollElapsed * 15f) * 0.85f);
                float liftB = liftScale * (0.29f + diceThrowStrength * 0.00028f);
                float offsetB = -lateral * 0.85f;
                dieInstanceB.transform.translate(offsetB, liftB - lastDiceLiftB, offsetB * 0.40f);
                lastDiceLiftB = liftB;
            }
            if (diceRollTime <= 0f) {
                diceRollElapsed = 0f;
                lastDiceLiftA = 0f;
                lastDiceLiftB = 0f;
                rebuildGameObjects();
                if (Gdx.input.isPeripheralAvailable(Input.Peripheral.Vibrator)) {
                    try {
                        Gdx.input.vibrate(35);
                    } catch (SecurityException ignored) {
                        // Haptics are optional and must never crash the game.
                    }
                }
            }
        }
        if (moveAnimating && movingPiece != null) {
            moveTime += delta;
            float t = MathUtils.clamp(moveTime / MOVE_DURATION, 0f, 1f);
            float eased = t * t * (3f - 2f * t);
            float arc = MathUtils.sin(MathUtils.PI * t) * 1.15f;
            tmp.set(moveStart).lerp(moveEnd, eased);
            tmp.y += arc;
            movingPiece.transform.setToTranslation(tmp);
            if (t >= 1f) {
                models.removeValue(movingPiece, true);
                movingPiece = null;
                moveAnimating = false;
                finishMoveState();
            }
        }

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(0.020f, 0.018f, 0.016f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);

        camera.update();
        batch.begin(camera);
        for (ModelInstance model : models) batch.render(model, environment);
        for (ModelInstance marker : moveMarkers) batch.render(marker, environment);
        batch.end();

        renderUi();
    }

    private void renderUi() {
        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();
        float top = Math.min(104f, h * 0.145f);

        uiShape.begin(ShapeRenderer.ShapeType.Filled);
        uiShape.setColor(0.018f, 0.024f, 0.026f, 0.97f);
        uiShape.rect(0f, h - top, w, top);
        uiShape.setColor(0.78f, 0.58f, 0.28f, 0.86f);
        uiShape.rect(0f, h - top, w, 2f);

        float cardW = Math.min(285f, w * 0.235f);
        float cardH = Math.min(66f, top - 18f);
        float cardY = h - top + 9f;
        float leftCardX = 72f;
        float rightCardX = w - cardW - 72f;

        uiShape.setColor(0.055f, 0.062f, 0.068f, 0.97f);
        uiShape.rect(leftCardX, cardY, cardW, cardH);
        uiShape.rect(rightCardX, cardY, cardW, cardH);

        uiShape.setColor(0.92f, 0.85f, 0.69f, 1f);
        uiShape.circle(leftCardX + 22f, cardY + cardH * 0.56f, 8f, 24);
        uiShape.setColor(0.23f, 0.11f, 0.05f, 1f);
        uiShape.circle(rightCardX + 22f, cardY + cardH * 0.56f, 8f, 24);

        float turnW = Math.min(320f, w * 0.28f);
        float turnX = (w - turnW) * 0.5f;
        uiShape.setColor(0.060f, 0.050f, 0.038f, 0.98f);
        uiShape.rect(turnX, cardY + 7f, turnW, cardH - 14f);
        uiShape.setColor(0.52f, 0.34f, 0.16f, 0.55f);
        uiShape.rect(turnX, cardY + 7f, turnW, 1.5f);
        uiShape.rect(turnX, cardY + cardH - 8.5f, turnW, 1.5f);

        float buttonY = cardY + cardH * 0.5f;
        uiShape.setColor(0.070f, 0.078f, 0.082f, 0.98f);
        uiShape.circle(28f, buttonY, 24f, 32);
        uiShape.circle(w - 28f, buttonY, 24f, 32);

        uiShape.setColor(0.90f, 0.86f, 0.76f, 1f);
        for (int i = -1; i <= 1; i++) {
            uiShape.rect(18f, buttonY + i * 7f - 1f, 20f, 2f);
        }
        uiShape.circle(w - 28f, buttonY, 6f, 24);
        for (int i = 0; i < 8; i++) {
            float a = MathUtils.PI2 * i / 8f;
            uiShape.rectLine(w - 28f + MathUtils.cos(a) * 6f,
                    buttonY + MathUtils.sin(a) * 6f,
                    w - 28f + MathUtils.cos(a) * 9f,
                    buttonY + MathUtils.sin(a) * 9f, 2.4f);
        }
        uiShape.end();

        uiBatch.begin();
        uiFont.getData().setScale(0.82f);
        uiFont.setColor(0.93f, 0.90f, 0.83f, 1f);
        uiFont.draw(uiBatch, "PLAYER 1", leftCardX + 38f, cardY + cardH * 0.67f);
        uiFont.draw(uiBatch, "PLAYER 2", rightCardX + 38f, cardY + cardH * 0.67f);

        uiFont.getData().setScale(0.60f);
        uiFont.setColor(0.56f, 0.59f, 0.60f, 1f);
        uiFont.draw(uiBatch, "SCORE  " + lightOff, leftCardX + 38f, cardY + cardH * 0.33f);
        uiFont.draw(uiBatch, "SCORE  " + darkOff, rightCardX + 38f, cardY + cardH * 0.33f);

        uiFont.getData().setScale(0.92f);
        uiFont.setColor(0.97f, 0.87f, 0.67f, 1f);
        uiFont.draw(uiBatch, "0", turnX + 28f, cardY + cardH * 0.58f);
        uiFont.draw(uiBatch, "0", turnX + turnW - 42f, cardY + cardH * 0.58f);

        uiFont.getData().setScale(0.80f);
        uiFont.setColor(0.97f, 0.92f, 0.80f, 1f);
        uiLayout.setText(uiFont, lightTurn ? "YOUR TURN" : "CPU TURN");
        uiFont.draw(uiBatch, uiLayout,
                turnX + turnW * 0.5f - uiLayout.width * 0.5f,
                cardY + cardH * 0.60f);

        uiFont.getData().setScale(0.58f);
        uiFont.setColor(0.53f, 0.57f, 0.58f, 1f);
        uiFont.draw(uiBatch, "BAR  " + lightBar + " / " + darkBar,
                16f, h - top - 12f);
        uiFont.draw(uiBatch, "OFF  " + lightOff + " / " + darkOff,
                105f, h - top - 12f);

        if (statusTimer > 0f) {
            uiFont.getData().setScale(0.62f);
            uiFont.setColor(0.80f, 0.70f, 0.50f, MathUtils.clamp(statusTimer, 0f, 1f));
            uiLayout.setText(uiFont, status);
            uiFont.draw(uiBatch, uiLayout,
                    w * 0.5f - uiLayout.width * 0.5f, 24f);
        }
        uiBatch.end();
    }

    @Override
    public boolean touchDown(int x, int y, int pointer, int button) {
        downX = lastX = x; downY = lastY = y; dragged = false;
        int dieHit = !moveAnimating && diceRollTime <= 0f ? hitDie(x, y) : -1;
        diceTouch = dieHit >= 0;
        if (diceTouch) {
            selectedDie = dieHit;
            diceSwipeDistance = 0f;
            if (diceRolled && !allDiceUsed()) {
                if (dieUsed[dieHit]) { status = "That die is already used"; statusTimer = 0.8f; selectedDie = -1; }
                else if (dieAllowedByTurn(dieHit)) { status = "Die " + dice[dieHit] + " selected"; statusTimer = 0.8f; rebuildMoveMarkers(); }
                else { status = "That die cannot be used now"; statusTimer = 0.9f; selectedDie = -1; }
            }
        }
        uiTouch = false; return true;
    }

    @Override
    public boolean touchDragged(int x, int y, int pointer) {
        if (diceTouch) {
            if (!diceRolled) {
                float dx=x-downX, dy=y-downY;
                diceSwipeDistance=MathUtils.clamp((float)Math.sqrt(dx*dx+dy*dy),0f,300f);
                if (diceSwipeDistance>12f) dragged=true;
            }
            lastX=x; lastY=y; return true;
        }
        if (Math.abs(x-downX)+Math.abs(y-downY)>12f) dragged=true;
        if (!dragged) return true;
        float dx=x-lastX, dy=y-lastY;
        cameraAzimuth=MathUtils.clamp(cameraAzimuth-dx*0.13f,-22f,22f);
        cameraElevation=MathUtils.clamp(cameraElevation-dy*0.09f,48f,68f);
        updateCamera(); lastX=x; lastY=y; return true;
    }

    @Override
    public boolean touchUp(int x, int y, int pointer, int button) {
        if (diceTouch) {
            if (!moveAnimating && diceRollTime<=0f && !diceRolled) rollDice(diceSwipeDistance*2.2f);
            diceTouch=false; dragged=false; return true;
        }
        if (!dragged) pickBoard(x,y); dragged=false; return true;
    }
    @Override
    public boolean touchCancelled(int x, int y, int pointer, int button) {
        dragged = false;
        uiTouch = false;
        return true;
    }

    @Override public boolean mouseMoved(int x, int y) { return false; }
    @Override public boolean keyTyped(char character) { return false; }
    @Override public boolean keyDown(int keycode) { return false; }
    @Override public boolean keyUp(int keycode) { return false; }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        cameraDistance = MathUtils.clamp(
                cameraDistance * (1f + amountY * 0.055f), 13.5f, 22f);
        updateCamera();
        return true;
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = Math.max(1, width);
        camera.viewportHeight = Math.max(1, height);
        camera.update();
    }

    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}

    @Override
    public void dispose() {
        if (woodGrainTexture != null) woodGrainTexture.dispose();
        if (woodNormalTexture != null) woodNormalTexture.dispose();
        if (lightCheckerTexture != null) lightCheckerTexture.dispose();
        if (lightCheckerNormalTexture != null) lightCheckerNormalTexture.dispose();
        if (darkCheckerTexture != null) darkCheckerTexture.dispose();
        if (darkCheckerNormalTexture != null) darkCheckerNormalTexture.dispose();
        if (damaskTexture != null) damaskTexture.dispose();
        if (checkerShadowTexture != null) checkerShadowTexture.dispose();
        for (Texture faceTexture : dieFaceTextures) {
            if (faceTexture != null) faceTexture.dispose();
        }
        if (doublingCubeTexture != null) doublingCubeTexture.dispose();
        if (studioCubemap != null) studioCubemap.dispose();
        batch.dispose();
        uiShape.dispose();
        uiBatch.dispose();
        uiFont.dispose();
        if (floorModel != null) floorModel.dispose();
        if (baseModel != null) baseModel.dispose();
        if (playingSurfaceModel != null) playingSurfaceModel.dispose();
        if (railModel != null) railModel.dispose();
        if (barModel != null) barModel.dispose();
        if (darkPointModel != null) darkPointModel.dispose();
        if (lightPointModel != null) lightPointModel.dispose();
        if (pointShadowModel != null) pointShadowModel.dispose();
        if (darkChecker != null) darkChecker.dispose();
        if (lightChecker != null) lightChecker.dispose();
        if (diceModel != null) diceModel.dispose();
        if (checkerShadowModel != null) checkerShadowModel.dispose();
        if (pointRailModel != null) pointRailModel.dispose();
        if (doublingCubeModel != null) doublingCubeModel.dispose();
        if (accentModel != null) accentModel.dispose();
        if (diceTrayModel != null) diceTrayModel.dispose();
        if (diceEdgeModel != null) diceEdgeModel.dispose();
        if (screwModel != null) screwModel.dispose();
        if (sideTrayModel != null) sideTrayModel.dispose();
        if (sideTrayInsetModel != null) sideTrayInsetModel.dispose();
        if (hingePlateModel != null) hingePlateModel.dispose();
        if (medallionModel != null) medallionModel.dispose();
        if (medallionRingModel != null) medallionRingModel.dispose();
        if (hudHeaderModel != null) hudHeaderModel.dispose();
        if (hudHeaderInsetModel != null) hudHeaderInsetModel.dispose();
        if (hudTurnModel != null) hudTurnModel.dispose();
    }
}