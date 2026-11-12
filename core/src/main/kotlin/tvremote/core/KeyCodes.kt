package tvremote.core

/** Android `KeyEvent` codes understood by Android TV. */
object KeyCodes {
    const val HOME = 3
    const val BACK = 4
    const val DIGIT_0 = 7 // 0..9 = 7..16
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val DPAD_CENTER = 23
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val POWER = 26
    const val SPACE = 62
    const val ENTER = 66
    const val DEL = 67
    const val MENU = 82
    const val SEARCH = 84
    const val MEDIA_PLAY_PAUSE = 85
    const val MEDIA_STOP = 86
    const val MEDIA_NEXT = 87
    const val MEDIA_PREVIOUS = 88
    const val MEDIA_REWIND = 89
    const val MEDIA_FAST_FORWARD = 90
    const val VOLUME_MUTE = 164
    const val INFO = 165
    const val CHANNEL_UP = 166
    const val CHANNEL_DOWN = 167
    const val TV = 170
    const val GUIDE = 172
    const val CAPTIONS = 175
    const val SETTINGS = 176
    const val TV_INPUT = 178
    const val PROG_RED = 183
    const val PROG_GREEN = 184
    const val PROG_YELLOW = 185
    const val PROG_BLUE = 186
    const val ASSIST = 219

    fun digit(n: Int): Int {
        require(n in 0..9)
        return DIGIT_0 + n
    }

    /**
     * Key code that types [c] in a TV text field, or null if there is none.
     * Letters are typed in lower case (the remote protocol has no shift state).
     */
    fun forChar(c: Char): Int? = when (c.lowercaseChar()) {
        in 'a'..'z' -> 29 + (c.lowercaseChar() - 'a')
        in '0'..'9' -> DIGIT_0 + (c - '0')
        ' ' -> SPACE
        ',' -> 55
        '.' -> 56
        '\t' -> 61
        '\n' -> ENTER
        '`' -> 68
        '-' -> 69
        '=' -> 70
        '[' -> 71
        ']' -> 72
        '\\' -> 73
        ';' -> 74
        '\'' -> 75
        '/' -> 76
        '@' -> 77
        '+' -> 81
        '*' -> 17
        '#' -> 18
        else -> null
    }
}
