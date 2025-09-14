package io.github.remmerw.saga


const val TEXT_NODE = "#text"
const val COMMENT_NODE = "#comment"
const val DOCTYPE_NODE = "#doctype"

enum class Tag {
    A, META, HEAD, B, TBODY, CAPTION, SMALL, SPACER, DD, DT,
    LINK, ANCHOR, TABLE, TD,
    TH, TR, STYLE, IMG, SCRIPT, SPAN, Q,
    BLOCKQUOTE, P, PRE, BODY, DL, DIV, FORM, INPUT,
    BUTTON, TEXTAREA, SELECT, OPTION, FRAMESET, FRAME,
    IFRAME, UL, OL, LI, HR, BR, OBJECT, APPLET, EMBED, FONT,
    BASEFONT, H1, H2, H3, H4, H5, H6, CANVAS, HTML, TITLE, BASE,
    CENTER, BIG, SECTION, NAV, SVG, USE, SUMMARY,
    DETAILS, HEADER, FOOTER, ARTICLE, NOSCRIPT, LABEL,
    TFOOT, THEAD, MAIN, PICTURE, SOURCE, STRONG, EM, I;

    fun tag(): String {
        return this.name.lowercase()
    }
}