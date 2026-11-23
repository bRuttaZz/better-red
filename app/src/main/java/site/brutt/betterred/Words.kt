package site.brutt.betterred

/**
 * EVERY piece of text the app shows lives in this file, so it can be reviewed and edited in one place.
 *
 * - Plain text is a `const val`.
 * - Text with something filled in (a name, a date, a number) is a small `fun`.
 * - "{n}" in wishes is replaced with her name (and removed cleanly if she hasn't set one).
 *
 * (The only exceptions are the launcher name in res/values/strings.xml and dates, which follow the phone's language.)
 */
object Words {
    const val APP_NAME = "Better Red"

    // ------------------------------------------------------------------ bottom tabs
    object Tabs {
        const val CALENDAR = "Calendar"
        const val NEWS = "Doctor said"
        const val PARTNER = "Partner"
        const val PROFILE = "Profile"
    }

    // ------------------------------------------------------------------ first launch
    object Welcome {
        const val TAGLINE = "Your cycle, your notes.\nA simple, private period tracker made for you. No account, and everything stays on your phone."
        const val GET_STARTED = "Get started"
        const val RESTORE = "Restore from a backup"
        const val BACK = "Back"

        const val WHO_TITLE = "Who is this for?"
        const val WHO_SUB = "You can change this later."
        const val FOR_ME = "For me"
        const val FOR_ME_SUB = "Track my own cycle, mood and symptoms."
        const val FOR_PARTNER = "For my partner"
        const val FOR_PARTNER_SUB = "See what my partner shares with me. Coming soon."
        const val CONTINUE = "Continue"

        const val SETUP_TITLE = "A little about you"
        const val SETUP_SUB = "An estimate is fine. Predictions adjust as you log more periods."
        const val NAME_LABEL = "Your name (optional)"
        const val NAME_HELP = "Used for greetings only"
        const val LAST_PERIOD = "Last period started"
        const val PICK_DATE = "Tap to pick a date"
        const val CYCLE_LENGTH = "Usual cycle length"
        const val CYCLE_LENGTH_SUB = "Start to start, in days"
        const val PERIOD_LENGTH = "Period length"
        const val PERIOD_LENGTH_SUB = "Days of bleeding"
        const val DONE = "Done"
        const val SKIP = "I don’t remember, skip for now"
    }

    // ------------------------------------------------------------------ calendar
    object Calendar {
        const val TITLE = "Calendar"
        const val JUMP_TO_TODAY = "Jump to today"
        const val PREV_MONTH = "Previous month"
        const val NEXT_MONTH = "Next month"
        const val OPEN_PROFILE = "Open profile"

        const val LEGEND_PERIOD = "Period"
        const val LEGEND_EXPECTED = "Expected"
        const val LEGEND_FERTILE = "Fertile"
        const val LEGEND_OVULATION = "Ovulation"
        const val SEX_LOGGED = "Sex logged"

        const val RING_DAY = "Day"
        const val NO_PREDICTION = "No prediction"
        const val BACK_TO_TODAY = "Back to today"

        const val HOW_ARE_YOU = "How are you feeling?"
        fun notesFor(date: String) = "Notes for $date"
        fun flowChip(flow: String) = "$flow flow"
        fun sexChip(kind: String) = "♥ $kind sex"

        fun daysLate(n: Int) = if (n == 1) "1 day late" else "$n days late"
        fun expectedOn(date: String) = "Expected $date"
        fun periodDay(n: Int) = "Day $n"
        const val OF_YOUR_PERIOD = "of your period"
        fun nextPeriodOn(date: String) = "Next period · $date"
        fun ovulationOn(date: String) = "Ovulation · $date"
        const val OVULATION = "Ovulation"
        fun cycleDay(n: Int) = "Day $n"
        fun ofCycle(length: Int) = "of a ~$length-day cycle"
        fun inDays(n: Int) = when (n) {
            0 -> "Today"
            1 -> "Tomorrow"
            else -> "in $n days"
        }

        const val FUTURE_NOTE = "Future dates show predictions only."
        const val PERIOD_DAY = "Period day"
        const val NOT_PERIOD_DAY = "Not a period day"
        const val BLOODY_NOTE = "Bloody note"
        const val EDIT_NOTE = "Edit note"
    }

    // ------------------------------------------------------------------ "Bloody note" sheet
    object Log {
        const val PERIOD = "Period"
        const val MOOD = "Mood"
        const val SYMPTOMS = "Symptoms"
        const val SEX = "Sex (optional)"
        const val NOTE = "Note"
        const val NONE = "None"
        const val NOTE_HINT = "Anything worth remembering?"
        fun noteCount(used: Int, max: Int) = "$used / $max"
        const val CANCEL = "Cancel"
        const val SAVE = "Save"
    }

    // Names shown for logged things (the database stores the ids, not these words).
    object Labels {
        const val FLOW_LIGHT = "Light"
        const val FLOW_MEDIUM = "Medium"
        const val FLOW_HEAVY = "Heavy"

        const val MOOD_HAPPY = "Happy"
        const val MOOD_CALM = "Calm"
        const val MOOD_TIRED = "Tired"
        const val MOOD_LOW = "Low"
        const val MOOD_IRRITABLE = "Irritable"
        const val MOOD_ANXIOUS = "Anxious"

        const val SEX_PROTECTED = "Protected"
        const val SEX_UNPROTECTED = "Unprotected"

        const val SYM_CRAMPS = "Cramps"
        const val SYM_HEADACHE = "Headache"
        const val SYM_BLOATING = "Bloating"
        const val SYM_BACKACHE = "Backache"
        const val SYM_ACNE = "Acne"
        const val SYM_TENDER = "Tender breasts"
        const val SYM_CRAVINGS = "Cravings"
        const val SYM_SPOTTING = "Spotting"
    }

    // ------------------------------------------------------------------ the home card (her day)
    object Day {
        const val MORNING = "Good morning"
        const val AFTERNOON = "Good afternoon"
        const val EVENING = "Good evening"

        // A wish for her, by phase. One is picked per day. Format: title to body.
        val WISH_PERIOD = listOf(
            "Be gentle with yourself, {n} 🤍" to "Rest when you can, keep water close, and let anything that can wait, wait.",
            "Cozy day, {n} ☕" to "A warm drink, a heat pad and an early night can make a big difference.",
            "You’re doing great, {n} 🌻" to "Your body is working hard. Slow down a little today.",
        )
        val WISH_EXPECTED = listOf(
            "Your period may arrive soon 🌸" to "Keep a pad or cup in your bag, and go easy on yourself if you feel off.",
        )
        val WISH_FOLLICULAR = listOf(
            "Fresh start, {n} 🌱" to "Energy usually climbs this week. A nice time to try something new.",
            "You’re blooming, {n} 🌷" to "Many people feel lighter and more focused now. Enjoy it.",
        )
        val WISH_MID = listOf(
            "Glow days, {n} ✨" to "Confidence and energy often peak around now. Go enjoy your day.",
            "Feeling bright, {n}? 💫" to "Mid-cycle is often when people feel most social and energetic.",
        )
        val WISH_LUTEAL = listOf(
            "Slow and steady, {n} 🍵" to "Cravings and mood dips are normal now. Comfort food is allowed.",
            "Wind-down week, {n} 🌙" to "Put sleep first and give yourself some extra slack.",
        )
        val WISH_NO_DATA = listOf(
            "Hi {n} 👋" to "Log your period to start seeing your cycle.",
        )

        // What a partner sees instead of a wish (partner mode, later).
        const val SUPPORT_PERIOD = "Offer a heat pad, tea, or to take a chore off {n}’s plate. Ask what would help."
        const val SUPPORT_EXPECTED = "{n}’s period may start soon. Check in and keep plans flexible."
        const val SUPPORT_FOLLICULAR = "A good week to plan something fun together."
        const val SUPPORT_MID = "Energy is often high now. Maybe plan a date or a walk together."
        const val SUPPORT_LUTEAL = "Extra patience and {n}’s favorite snacks go a long way this week."
        const val SUPPORT_NO_DATA = "Nothing shared yet."

        // Phase names
        const val PHASE_PERIOD = "Period"
        const val PHASE_EXPECTED = "Period expected"
        const val PHASE_FOLLICULAR = "Follicular phase"
        const val PHASE_FERTILE = "Fertile window"
        const val PHASE_OVULATION = "Ovulation day"
        const val PHASE_MID = "Mid-cycle" // shown instead of fertile/ovulation when fertility info is hidden
        const val PHASE_LUTEAL = "Luteal phase"

        // Short tips (for widgets etc.)
        const val TIP_PERIOD = "Rest, hydrate, and go easy on yourself."
        const val TIP_EXPECTED = "Your period may start around now."
        const val TIP_FOLLICULAR = "Energy often starts to pick up."
        const val TIP_FERTILE = "Pregnancy is possible on these days."
        const val TIP_OVULATION = "Most fertile day of the cycle."
        const val TIP_MID = "Energy is often at its highest around mid-cycle."
        const val TIP_LUTEAL = "PMS symptoms can show up in this phase."

        const val CHANCE_LOW = "Low pregnancy chance"
        const val CHANCE_MEDIUM = "Moderate pregnancy chance"
        const val CHANCE_HIGH = "High pregnancy chance"
    }

    // ------------------------------------------------------------------ how far back the app goes (History.YEARS)
    object History {
        fun calendarLimit(years: Int) = "Better Red shows the last $years years."
        fun profileTitle(years: Int) = "Keeps the last $years years"
        const val PROFILE_SUB = "Older days are deleted from this phone when the app opens. Export a backup to keep them."
    }

    // ------------------------------------------------------------------ news ("Doctor said")
    object News {
        const val TITLE = "…and the doctor said"
        const val SUBTITLE = "Reads from women’s health non-profits"
        const val SOURCES = "Sources"
        const val REFRESH = "Refresh"
        const val NEW = "New" // shown next to the red square on articles she hasn't opened yet
        const val EMPTY = "Nothing to read yet"
        const val EMPTY_SUB = "Turn on a source, or add your own feed."
        const val OFFLINE = "Couldn’t load new articles. Check your internet and try again."
        const val TRY_AGAIN = "Try again"
        fun couldNotLoad(source: String) = "Couldn’t load $source"
        fun meta(source: String, ago: String) = if (ago.isEmpty()) source else "$source · $ago"

        // Relative time
        const val JUST_NOW = "just now"
        fun minutesAgo(n: Long) = "${n}m ago"
        fun hoursAgo(n: Long) = "${n}h ago"
        fun daysAgo(n: Long) = "${n}d ago"

        // Sources sheet
        const val BUILT_IN = "Built in"
        fun yourFeeds(count: Int, max: Int) = "Your feeds ($count of $max)"
        const val ADD_FEED = "Add an RSS feed"
        const val FEED_ADDRESS = "Feed address"
        const val FEED_ADDRESS_HINT = "https://example.org/feed"
        const val ADD = "Add"
        const val ADDING = "Checking…"
        const val REMOVE = "Remove"
        const val CLOSE = "Close"
        fun maxFeeds(max: Int) = "You can add up to $max feeds."
        const val BAD_LINK = "That doesn’t look like a web address."
        const val NOT_A_FEED = "Couldn’t find a feed at that address."
        const val ALREADY_ADDED = "That feed is already added."
        const val FEED_ADDED = "Feed added"

        // Reading an article
        const val OPEN_IN_BROWSER = "Open in browser"
        const val READER_LOADING = "Getting the article ready…"
        const val READER_FAILED = "Couldn’t load this article. Check your internet, or open it in your browser."
        const val SHOW_ORIGINAL = "Show original page"
        const val SHOW_READER = "Show reader view"
        fun by(name: String) = "By $name"
    }

    // ------------------------------------------------------------------ home-screen widgets
    // (The names shown in the widget picker live in res/values/strings.xml, because Android requires that.)
    object Widget {
        const val NO_DATA = "Log your period to start"
        fun periodIn(days: Int) = when (days) {
            0 -> "Period expected today"
            1 -> "Period tomorrow"
            else -> "Period in $days days"
        }
        fun periodDay(n: Int) = "Day $n of your period"
        const val NEWS_EMPTY = "Open Better Red to load articles"
        fun newsMeta(isNew: Boolean, source: String, ago: String) =
            listOfNotNull(if (isNew) "■ New" else null, source, ago.ifEmpty { null }).joinToString(" · ")
    }

    // ------------------------------------------------------------------ partner (placeholder for now)
    object Partner {
        const val SOON_TITLE = "Partner sharing is on the way"
        const val SOON_BODY = "Pair by QR code. You decide what they can see. End-to-end encrypted, no accounts."
    }

    // ------------------------------------------------------------------ profile
    object Profile {
        const val NO_NAME = "Your profile"
        const val EDIT_NAME = "Edit name"
        const val CHANGE_ICON = "Change profile icon"
        const val LOCAL_ONLY = "No account · stored on this phone"

        const val SECTION_CYCLE = "Your cycle"
        const val AVG_CYCLE = "avg cycle"
        const val AVG_PERIOD = "avg period"
        const val PERIODS_LOGGED = "periods logged"
        fun lastCycles(n: Int) = "Last $n cycles (days)"
        const val SHOW_FERTILITY = "Show fertility info"
        const val SHOW_FERTILITY_SUB = "Fertile window, ovulation and pregnancy chance"
        const val LOG_SEX = "Log sex"
        const val LOG_SEX_SUB = "Optional. Adds a “Sex” row to notes and ♥ on the calendar"
        const val AUTO_ADJUST = "Auto-adjust predictions"
        const val AUTO_ADJUST_SUB = "Learns from your last 6 cycles"
        const val CYCLE_LENGTH = "Cycle length"
        const val PERIOD_LENGTH = "Period length"
        const val LUTEAL = "Luteal phase"
        const val LUTEAL_SUB = "Days from ovulation to period"
        const val CALCULATED = "Calculated"
        const val SET_BY_YOU = "Set by you"
        const val DECREASE = "Decrease"
        const val INCREASE = "Increase"

        const val SECTION_LOOK = "Appearance"
        const val THEME = "Theme"
        const val THEME_SYSTEM = "System"
        const val THEME_LIGHT = "Light"
        const val THEME_DARK = "Dark"
        const val COLOR = "Color"
        const val COLOR_RED = "Red"
        const val COLOR_GREEN = "Leaf"
        const val COLOR_YELLOW = "Sunflower"
        const val COLOR_DYNAMIC = "Wallpaper"
        const val COLOR_DYNAMIC_SUB = "“Wallpaper” follows your phone’s colors"
        const val COLOR_DYNAMIC_UNAVAILABLE = "“Wallpaper” colors need Android 12 or newer"

        const val SECTION_DATA = "Your data"
        const val EXPORT = "Export database"
        const val EXPORT_SUB = "Save a .db file you can move to another phone"
        const val IMPORT = "Import database"
        const val IMPORT_SUB = "Replace data on this phone with a backup"
        const val DELETE = "Delete all data"
        const val DELETE_SUB = "Cannot be undone"

        const val SECTION_ABOUT = "About"
        fun version(name: String) = "Better Red $name" // name comes from versionName in app/build.gradle.kts
        const val VERSION_SUB = "No servers. No tracking. Made with love."
        const val LICENSE = "Free software"
        const val LICENSE_SUB = "GNU GPL v3: free to use, study, share and change"
        const val OSS = "Open-source libraries"
        const val OSS_SUB = "The libraries Better Red is built with"
        const val OSS_TEXT = """Better Red is free software under the GNU GPL v3. It uses these open-source libraries:

• AndroidX, Jetpack Compose, Material 3 and Material Icons, Core Splashscreen — The Android Open Source Project, Apache License 2.0
• Kotlin standard library, kotlinx.coroutines, JetBrains annotations — JetBrains s.r.o. and contributors, Apache License 2.0
• JSpecify — The JSpecify Authors, Apache License 2.0
• Guava ListenableFuture — The Guava Authors, Apache License 2.0
• jsoup — Jonathan Hedley, MIT License

Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0

The MIT License

Copyright (c) 2009-2024 Jonathan Hedley <https://jsoup.org/>

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the \"Software\"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE."""
        const val CREDITS = "Credits"
        const val CREDITS_SUB = "Who made Better Red, and its source code"
        const val NOT_MEDICAL = "Not medical advice"
        const val NOT_MEDICAL_SUB = "Predictions are estimates. Not a form of birth control."

        // Dialogs
        const val NAME_TITLE = "Your name"
        const val NAME_BODY = "Only used for greetings. Stays on this phone."
        const val NAME_HINT = "Name or nickname"
        const val ICON_TITLE = "Profile icon"
        const val IMPORT_TITLE = "Import database?"
        const val IMPORT_BODY = "Data on this phone will be replaced by the backup. Export first if you want to keep what’s here now."
        const val CHOOSE_FILE = "Choose file"
        const val DELETE_TITLE = "Delete all data?"
        const val DELETE_BODY = "This removes every period, mood, note and setting from this phone. It cannot be undone."
        const val DELETE_CONFIRM = "Delete"
        const val SAVE = "Save"
        const val CANCEL = "Cancel"
        const val DONE = "Done"
        const val OK = "OK"
        fun exportFileName(date: String) = "better-red-$date.db"
    }

    // ------------------------------------------------------------------ credits (Profile → About → Credits)
    object Credits {
        const val TITLE = "Credits"
        const val AUTHOR = "Author"
        const val AUTHOR_NAME = "bruttazz"
        const val AUTHOR_URL = "https://brutt.site"
        const val SOURCE = "Source code"
        const val SOURCE_URL = "https://github.com/bRuttaZz/better-red"
        const val FOOTER = "Made with love for Better."
        const val OPENS_IN_BROWSER = "Opens in your browser"
        /** The link shown under a name: the address without "https://". */
        fun shown(url: String) = url.removePrefix("https://")
    }

    // ------------------------------------------------------------------ short messages (snackbar)
    object Messages {
        const val SAVED = "Saved"
        const val PERIOD_LOGGED = "Period day logged. Predictions updated."
        const val PERIOD_REMOVED = "Period day removed"
        const val ALL_SET = "All set"
        const val BACKUP_SAVED = "Backup saved"
        const val BACKUP_RESTORED = "Backup restored"
        const val ALL_DELETED = "All data deleted"
        const val CANT_OPEN_LOCATION = "Couldn’t open that location"
        const val CANT_OPEN_FILE = "Couldn’t open that file"
        const val NOT_A_BACKUP = "That file isn’t a Better Red backup."
        const val BACKUP_TOO_NEW = "This backup is from a newer version of Better Red. Update the app first."
        const val SOMETHING_WRONG = "Something went wrong"
    }
}
