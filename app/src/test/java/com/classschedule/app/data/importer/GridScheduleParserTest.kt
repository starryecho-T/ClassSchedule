package com.classschedule.app.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 夹具取自 2026-10-17 真机导出的 iedu.jlu.edu.cn「我的课表」第 6 周快照。 */
class GridScheduleParserTest {

    private fun card(
        week: Int,
        begin: Int,
        end: Int,
        name: String,
        teacher: String,
        time: String,
    ) = GridCard(week, begin, end, name, teacher, time)

    private fun assertBlocked(result: ScheduleParseResult) {
        assertFalse(result.canImport)
        assertTrue("Expected a blocking issue", result.issues.isNotEmpty())
    }

    @Test fun parsesRealSnapshotCards() {
        val result = GridScheduleParser.parse(
            listOf(
                card(1, 1, 2, "概率论与数理统计B[07]", "李亚军", "1-17周,星期1,第1节-第2节,前卫-李四光楼-206（地学优先）"),
                card(2, 1, 2, "面向对象程序设计[01]", "路兴昌", "1-12周,星期2,第1节-第2节,前卫-李四光楼-308（地学优先）"),
                card(4, 1, 2, "岩石学B", "刘娜", "1-2周,4周,星期4,第1节-第2节,前卫-李四光楼-第二阶梯（地学优先）"),
                card(1, 9, 12, "形势与政策Ⅲ", "于国伟", "3-8周,星期1,第9节-第12节,线上教学-前卫"),
                card(2, 9, 12, "大学物理BⅡ", "梁伟锋", "10周,星期2,第9节-第12节,线上教学-前卫"),
                card(1, 9, 12, "思想政治理论课实践教学", "张晋瑞", "2周,9周,星期1,第9节-第12节,前卫-李四光楼-208（地学优先）"),
                card(6, 1, 4, "劳动教育", "王笑严", "3-15周,星期6,第1节-第4节,"),
                card(2, 7, 8, "体育Ⅲ", "张艳红", "3-17周,星期2,第7节-第8节,"),
            ),
        )
        assertTrue(result.issues.toString(), result.canImport)
        assertEquals(8, result.courses.size)
        val probability = result.courses[0]
        assertEquals("概率论与数理统计B", probability.name)
        assertEquals("李亚军", probability.teacher)
        assertEquals("前卫-李四光楼-206（地学优先）", probability.room)
        assertEquals(1, probability.dayOfWeek)
        assertEquals(1..2, probability.startPeriod..probability.endPeriod)
        assertEquals((1..17).toSet(), probability.weeks)
        assertEquals(setOf(1, 2, 4), result.courses[2].weeks)
        assertEquals("前卫-李四光楼-第二阶梯（地学优先）", result.courses[2].room)
        assertEquals(9..12, result.courses[3].let { it.startPeriod..it.endPeriod })
        assertEquals(setOf(2, 9), result.courses[5].weeks)
        assertEquals("", result.courses[6].room)
        assertEquals(1..4, result.courses[6].let { it.startPeriod..it.endPeriod })
        assertEquals(setOf(10), result.courses[4].weeks)
    }

    @Test fun stripsTeachingClassSuffixButNeverBlanksTheName() {
        val result = GridScheduleParser.parse(
            listOf(card(1, 1, 2, "[07]", "李亚军", "1-17周,星期1,第1节-第2节,前卫-李四光楼-206")),
        )
        assertTrue(result.canImport)
        assertEquals("[07]", result.courses.single().name)
    }

    @Test fun missingCardAttributesFallBackToTextOnlyParsing() {
        val result = GridScheduleParser.parse(
            listOf(card(0, 0, 0, "地图学", "王明常", "2-17周,星期3,第1节-第2节,前卫-敬信教学楼-D区305")),
        )
        assertTrue(result.canImport)
        assertEquals(3, result.courses.single().dayOfWeek)
    }

    @Test fun attributeDayMismatchBlocksTheWholeBatch() {
        assertBlocked(
            GridScheduleParser.parse(
                listOf(card(2, 1, 2, "地图学", "王明常", "2-17周,星期3,第1节-第2节,前卫-敬信教学楼-D区305")),
            ),
        )
    }

    @Test fun attributePeriodMismatchBlocksTheWholeBatch() {
        assertBlocked(
            GridScheduleParser.parse(
                listOf(card(3, 3, 4, "体育Ⅲ", "张艳红", "3-17周,星期3,第7节-第8节,")),
            ),
        )
    }

    @Test fun missingWeekSegmentBlocksTheWholeBatch() {
        assertBlocked(
            GridScheduleParser.parse(
                listOf(card(1, 1, 2, "概率论与数理统计B", "李亚军", "星期1,第1节-第2节,前卫-李四光楼-206")),
            ),
        )
    }

    @Test fun multipleTimingsInOneCardBlockTheWholeBatch() {
        assertBlocked(
            GridScheduleParser.parse(
                listOf(card(1, 1, 2, "概率论与数理统计B", "李亚军", "1-17周,星期1,第1节-第2节,星期2,第3节-第4节")),
            ),
        )
    }

    @Test fun weekBeyondTermBlocksTheWholeBatch() {
        assertBlocked(
            GridScheduleParser.parse(
                listOf(card(1, 1, 2, "概率论与数理统计B", "李亚军", "1-18周,星期1,第1节-第2节,前卫-李四光楼-206")),
            ),
        )
    }

    @Test fun blankNameBlocksTheWholeBatch() {
        assertBlocked(GridScheduleParser.parse(listOf(card(1, 1, 2, " ", "李亚军", "1-17周,星期1,第1节-第2节,X"))))
    }

    @Test fun oversizeInputIsRefusedBeforeParsing() {
        val cards = (1..301).map { card(1, 1, 2, "课$it", "师", "1-17周,星期1,第1节-第2节,楼") }
        assertBlocked(GridScheduleParser.parse(cards))
    }
}
