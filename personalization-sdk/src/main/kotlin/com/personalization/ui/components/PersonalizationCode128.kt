package com.personalization.ui.components

/**
 * Кодировщик штрихкода Code 128 — то, что рисует [PersonalizationBarcode].
 *
 * Номер карты лояльности в макете (Wallet/Code, 520:8886) — линейный штрихкод, а Code 128 —
 * формат, который читают кассовые сканеры и Apple Wallet. Библиотеку ради сотни строк
 * таблицы SDK не тянет.
 *
 * Набор выбирается целиком на строку: одни цифры — набор C (две цифры на символ, код вдвое
 * короче), при нечётной длине последняя цифра уходит в набор B через переключатель CODE B.
 * Всё прочее — набор B, печатный ASCII 32…126. Строку с другими символами Code 128 без
 * расширений не несёт — тогда кодировщик возвращает null, и штрихкода нет.
 */
internal object PersonalizationCode128 {

    /** Символы строки: значения по порядку, от старт-символа до контрольного включительно. */
    fun values(text: String): List<Int>? {
        if (text.isEmpty() || text.any { it.code !in PRINTABLE }) return null
        val values = mutableListOf<Int>()
        if (text.length >= 2 && text.all { it in '0'..'9' }) {
            values += START_C
            val pairs = text.length / 2
            for (i in 0 until pairs) values += text.substring(i * 2, i * 2 + 2).toInt()
            if (text.length % 2 == 1) {
                values += CODE_B
                values += text.last().code - 32
            }
        } else {
            values += START_B
            text.forEach { values += it.code - 32 }
        }
        values += checksum(values)
        return values
    }

    /** Модули слева направо, `true` — штрих. Тихих зон по краям нет — их даёт подложка. */
    fun encode(text: String): BooleanArray? {
        val symbols = values(text) ?: return null
        val modules = ArrayList<Boolean>(symbols.size * 11 + 13)
        (symbols + STOP).forEach { value ->
            PATTERNS[value].forEachIndexed { index, width ->
                repeat(width - '0') { modules += index % 2 == 0 }
            }
        }
        return modules.toBooleanArray()
    }

    /** Старт-символ плюс сумма значений, взвешенных позицией с единицы, по модулю 103. */
    private fun checksum(values: List<Int>): Int {
        var sum = values[0]
        for (i in 1 until values.size) sum += i * values[i]
        return sum % 103
    }

    private val PRINTABLE = 32..126
    private const val CODE_B = 100
    private const val START_B = 104
    private const val START_C = 105
    private const val STOP = 106

    /** Ширины штрихов и пробелов символа, начиная со штриха; индекс — значение символа. */
    private val PATTERNS = arrayOf(
        "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312",
        "132212", "221213", "221312", "231212", "112232", "122132", "122231", "113222",
        "123122", "123221", "223211", "221132", "221231", "213212", "223112", "312131",
        "311222", "321122", "321221", "312212", "322112", "322211", "212123", "212321",
        "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
        "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121",
        "313121", "211331", "231131", "213113", "213311", "213131", "311123", "311321",
        "331121", "312113", "312311", "332111", "314111", "221411", "431111", "111224",
        "111422", "121124", "121421", "141122", "141221", "112214", "112412", "122114",
        "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
        "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112",
        "421211", "212141", "214121", "412121", "111143", "111341", "131141", "114113",
        "114311", "411113", "411311", "113141", "114131", "311141", "411131", "211412",
        "211214", "211232", "2331112"
    )
}
