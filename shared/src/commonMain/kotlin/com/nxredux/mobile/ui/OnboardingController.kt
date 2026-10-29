package com.nxredux.mobile.ui

/**
 * The onboarding steps AppState draws as [Screen.Onboarding]. WELCOME opens a first launch, then TEXT_SIZE (asked first:
 * every page after it reads at the size picked), then SYSTEM_BUTTONS with a controller connected (its SELECT, START and
 * MENU; also shown on its own for a new controller model later), then BOTTOM_SCREEN on a device with a built-in second screen; the folder
 * steps (All files access, the home folder, an extra folder, Android games) are AppState's own screens between those and
 * the first of the rest ([onboardingSteps], from HANDHELD), DONE last. In step order: the menu pad stays hidden before PAD
 * ([AppState.menuPadShown] compares by it), and a step off the list is placed by it.
 */
enum class OnboardingStep {
    WELCOME, TEXT_SIZE, SYSTEM_BUTTONS, BOTTOM_SCREEN, HANDHELD, SAVE_FORMATS, IMPORT_SAVES, SYNC_OFFER, ARTWORK, RA_SIGN_IN, RA_DOWNLOAD,
    CUSTOMISE, PAD, TITLE, TAB_HOME, TAB_CONSOLES, TAB_COLLECTIONS, TAB_TOOLS, GAME_LISTS,
    ACCENT, DONE,
}

/**
 * What decides which steps show: the handheld answer ([handheld], null while unanswered), a RetroAchievements sign-in,
 * the customise answer ([customise]), and the device: [padPage] (portrait with no controller: the menu pad is a choice),
 * [buttons] (a controller, or the pad shown: the page title and hints can hide).
 */
data class OnboardingContext(
    val signedIn: Boolean = false, val handheld: Boolean? = null,
    val customise: Boolean? = null, val padPage: Boolean = false, val buttons: Boolean = false,
)

/**
 * The steps after the folder phase, in order: the handheld's formats and sync only with one (the save import between
 * them, so imported saves sync too; always offered), the download only signed in.
 */
fun onboardingSteps(ctx: OnboardingContext): List<OnboardingStep> = buildList {
    add(OnboardingStep.HANDHELD)
    if (ctx.handheld == true) add(OnboardingStep.SAVE_FORMATS)
    add(OnboardingStep.IMPORT_SAVES)
    if (ctx.handheld == true) add(OnboardingStep.SYNC_OFFER)
    add(OnboardingStep.ARTWORK)
    // RetroAchievements right after artwork: both fetch in the background while the layout, accent and pad are picked.
    add(OnboardingStep.RA_SIGN_IN)
    if (ctx.signedIn) add(OnboardingStep.RA_DOWNLOAD)
    add(OnboardingStep.CUSTOMISE)
    if (ctx.customise == true) {
        if (ctx.padPage) add(OnboardingStep.PAD)
        if (ctx.buttons) add(OnboardingStep.TITLE)
        add(OnboardingStep.TAB_HOME); add(OnboardingStep.TAB_CONSOLES); add(OnboardingStep.TAB_COLLECTIONS); add(OnboardingStep.TAB_TOOLS)
        add(OnboardingStep.GAME_LISTS)
    }
    add(OnboardingStep.ACCENT)
    add(OnboardingStep.DONE)
}

/** Where onboarding is: the step shown (null when not running) and its answers. */
class OnboardingController {
    var step: OnboardingStep? = null
        private set
    var context = OnboardingContext()

    fun startWelcome() { step = OnboardingStep.WELCOME }

    /** The text size question, right after the welcome (before the folder steps). */
    fun startTextSize() { step = OnboardingStep.TEXT_SIZE }

    /** The controller's SELECT / START / MENU page, right after the text size (with a controller connected). */
    fun startSystemButtons() { step = OnboardingStep.SYSTEM_BUTTONS }

    /** The bottom screen question, right after the text size on a device with a built-in second screen (before the folder steps). */
    fun startBottomScreen() { step = OnboardingStep.BOTTOM_SCREEN }

    /** The post-folder steps from [resume] when it still applies, else the first of them. */
    fun startMain(resume: OnboardingStep? = null) {
        // A customise page saved before a kill: the answer was yes, and the page (or the next that applies here) resumes.
        if (resume != null && resume > OnboardingStep.CUSTOMISE && resume < OnboardingStep.ACCENT) {
            context = context.copy(customise = true)
            step = onboardingSteps(context).firstOrNull { it >= resume }
            return
        }
        val steps = onboardingSteps(context)
        step = resume?.takeIf { it in steps } ?: steps.first()
    }

    /** The step after this one, or null past DONE (onboarding finished: [step] is null then). A step the context took off the list goes on by step order. */
    fun next(): OnboardingStep? {
        val steps = onboardingSteps(context)
        val at = step
        step = if (at == null) steps.firstOrNull() else steps.firstOrNull { it > at }
        return step
    }

    /**
     * The step before this one; WELCOME and the first post-folder step ([isFirstMain]: the folder steps are not undone)
     * stay where they are, TEXT_SIZE goes to WELCOME, SYSTEM_BUTTONS to TEXT_SIZE, and BOTTOM_SCREEN to SYSTEM_BUTTONS
     * with a [controller] connected (it was asked), else to TEXT_SIZE.
     */
    fun back(controller: Boolean = false): OnboardingStep? {
        val s = step ?: return null
        if (s == OnboardingStep.WELCOME) return s
        if (s == OnboardingStep.TEXT_SIZE) { step = OnboardingStep.WELCOME; return step }
        if (s == OnboardingStep.SYSTEM_BUTTONS) { step = OnboardingStep.TEXT_SIZE; return step }
        if (s == OnboardingStep.BOTTOM_SCREEN) { step = if (controller) OnboardingStep.SYSTEM_BUTTONS else OnboardingStep.TEXT_SIZE; return step }
        val steps = onboardingSteps(context)
        step = steps.lastOrNull { it < s } ?: s
        return step
    }

    /** On the first post-folder step (none of them before it, even off the list): B from it goes back to the folder steps. */
    fun isFirstMain(): Boolean {
        val s = step ?: return false
        return s > OnboardingStep.BOTTOM_SCREEN && onboardingSteps(context).none { it < s }
    }

    fun finish() { step = null }

    /** "Step n of m" over [onboardingSteps]; (0, 0) on WELCOME, TEXT_SIZE, SYSTEM_BUTTONS, BOTTOM_SCREEN or when not running. */
    fun position(): Pair<Int, Int> {
        val s = step ?: return 0 to 0
        if (s <= OnboardingStep.BOTTOM_SCREEN) return 0 to 0
        val steps = onboardingSteps(context)
        // A step the context took off the list (a rotation, a controller) still counts where it stands.
        return if (s in steps) (steps.indexOf(s) + 1) to steps.size else (steps.count { it < s } + 1) to (steps.size + 1)
    }
}
