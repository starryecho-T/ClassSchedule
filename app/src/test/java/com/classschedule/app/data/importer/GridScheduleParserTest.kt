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
                // 真实页面 data-end-unit == data-begin-unit，跨度由 rowspan 表达；
                // 以下 begin/end 已是 capture 脚本推导后的值（真实快照 2026-10-17）。
                card(1, 1, 2, "概率论与数理统计B[07]", "李亚军", "1-17周,星期1,第1节-第2节,前卫-李四光楼-206（地学优先）"),
                card(2, 1, 2, "面向对象程序设计[01]", "路兴昌", "1-12周,星期2,第1节-第2节,前卫-李四光楼-308（地学优先）"),
                card(4, 1, 2, "岩石学B", "刘娜", "1-2周,4周,星期4,第1节-第2节,前卫-李四光楼-第二阶梯（地学优先）"),
                card(4, 1, 2, "岩石学B", "苟军", "10-12周,星期4,第1节-第2节,构造地质学实验室132"),
                card(1, 9, 12, "形势与政策Ⅲ", "于国伟", "3-8周,星期1,第9节-第12节,线上教学-前卫"),
                card(2, 9, 12, "大学物理BⅡ", "梁伟锋", "10周,星期2,第9节-第12节,线上教学-前卫"),
                card(1, 9, 12, "思想政治理论课实践教学", "张晋瑞", "2周,9周,星期1,第9节-第12节,前卫-李四光楼-208（地学优先）"),
                // 4 节跨度格子里的 5-6 节卡片：包含关系，必须接受。
                card(6, 5, 8, "劳动教育", "王笑严", "3-15周,星期6,第5节-第8节,"),
                card(6, 5, 8, "形势与政策Ⅲ", "黄丽娜", "3-4周,星期6,第5节-第6节,前卫-李四光楼-109（地学优先）"),
                card(2, 7, 8, "体育Ⅲ", "张艳红", "3-17周,星期2,第7节-第8节,"),
            ),
        )
        assertTrue(result.issues.toString(), result.canImport)
        assertEquals(10, result.courses.size)
        val probability = result.courses[0]
        assertEquals("概率论与数理统计B", probability.name)
        assertEquals("李亚军", probability.teacher)
        assertEquals("前卫-李四光楼-206（地学优先）", probability.room)
        assertEquals(1, probability.dayOfWeek)
        assertEquals(1..2, probability.startPeriod..probability.endPeriod)
        assertEquals((1..17).toSet(), probability.weeks)
        assertEquals(setOf(1, 2, 4), result.courses[2].weeks)
        assertEquals("前卫-李四光楼-第二阶梯（地学优先）", result.courses[2].room)
        assertEquals(9..12, result.courses[4].let { it.startPeriod..it.endPeriod })
        assertEquals(setOf(2, 9), result.courses[6].weeks)
        assertEquals(5..6, result.courses[8].let { it.startPeriod..it.endPeriod })
        assertEquals("", result.courses[9].room)
        assertEquals(1..4, result.courses[0].let { 1..4 })
        assertEquals(setOf(10), result.courses[5].weeks)
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

    @Test fun textPeriodOutsideCellSpanBlocksTheWholeBatch() {
        // 文本 3-4 节超出格子跨度 1-2，必须拒绝。
        assertBlocked(
            GridScheduleParser.parse(
                listOf(card(1, 1, 2, "概率论与数理统计B", "李亚军", "1-17周,星期1,第3节-第4节,前卫-李四光楼-206")),
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

    /** 真实场景 2026-10-17：实验课按具体日期排课，卡片名为「10月13日」，时间无周次无星期。 */
    @Test fun dateNamedLabCourseDerivesWeekFromDate() {
        val result = GridScheduleParser.parse(
            listOf(
                card(1, 1, 2, "概率论与数理统计B[07]", "李亚军", "1-17周,星期1,第1节-第2节,前卫-李四光楼-206（地学优先）"),
                // 10月13日 = 第 6 周周二；格子属性 week=2，节次 1-8。
                card(2, 1, 8, "10月13日", "机能科学实验平台", "第1节-第8节,机能科学实验平台309"),
                card(2, 1, 8, "10月13日", "机能科学实验平台", "第1节-第8节,机能科学实验平台306"),
            ),
        )
        assertTrue(result.issues.toString(), result.canImport)
        assertEquals(3, result.courses.size)
        val lab = result.courses[1]
        assertEquals(2, lab.dayOfWeek)
        assertEquals(1..8, lab.startPeriod..lab.endPeriod)
        assertEquals(setOf(6), lab.weeks)
        assertEquals("机能科学实验平台309", lab.room)
        assertTrue(lab.name.contains("10月13日"))
    }

    /** 日期与格子位置矛盾时必须拒绝，不能猜。 */
    @Test fun dateNamedCardPositionMismatchBlocks() {
        assertBlocked(
            GridScheduleParser.parse(
                // 10月13日是周二（week=2），故意放 week=5。
                listOf(card(5, 1, 8, "10月13日", "师", "第1节-第8节,实验室")),
            ),
        )
    }

    /** 非日期命名的无周次卡片依旧整批拒绝。 */
    @Test fun weeklessNonDateCardStillBlocked() {
        assertBlocked(
            GridScheduleParser.parse(
                listOf(card(1, 1, 2, "普通课", "师", "星期1,第1节-第2节,楼")),
            ),
        )
    }
}
