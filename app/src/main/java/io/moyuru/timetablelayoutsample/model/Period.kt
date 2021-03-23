package io.moyuru.timetablelayoutsample.model

import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneOffset
import java.util.concurrent.TimeUnit

sealed class Period(
  open val startAt: Long,
  open val endAt: Long,
  open val stageNumber: Int
)

data class EmptyPeriod(
  override val startAt: Long,
  override val endAt: Long,
  override val stageNumber: Int
) : Period(startAt, endAt, stageNumber)

data class Program(
  val bandName: String,
  override val startAt: Long,
  override val endAt: Long,
  override val stageNumber: Int
) : Period(startAt, endAt, stageNumber)

private fun List<String>.toPrograms(
  firstStartAt: Long,
  duration: Long,
  restDuration: Long,
  stageNumber: Int
): List<Program> {
  var startAt = firstStartAt
  return map {
    //Programのmapを生成する
    Program(it, startAt, startAt + duration, stageNumber)
        //Programが進むにつれてrestDurationを追加
      .also { startAt += duration + restDuration }
  }
}

fun createPrograms(): List<Program> {
  val lists = listOf(
    listOf(
      "It Prevails",
      "The Price of Agony",
      "Confession",
      "A Day To Remember",
      "Mikoto",
      "Forever Cadence",
      "Altars",
      "Hand of Mercy",
      "Handredth",
      "The Amity Affliction",
      "Napoleon",
      "For the Fallen Dreams",
      "Counterparts",
      "Misery Signals",
      "The Ghost Inside"
    ), listOf(
      "Betraying the Martyrs",
      "Darkest Hour",
      "Erra",
      "Miss May I",
      "Obey the Brave",
      "Haven Shall Burn",
      "As Blood Runs Black",
      "I Killed The Prom Queen",
      "Parkway Drive",
      "August Burns Red",
      "All That Remains",
      "Lamb of God",
      "As I Lay Dying",
      "Unearth",
      "Killswitch Engage"
    ),
    listOf(
      "American Me",
      "Fathoms",
      "Wage War",
      "Rise of the North Star",
      "Awaken Demons",
      "Legend",
      "Nasty",
      "Thick as Blood",
      "Crystal Lake",
      "Wage War",
      "Architects",
      "Betrayal",
      "Emmure",
      "The Acasia Strain",
      "Hatebreed"
    ), listOf(
      "Carnifex",
      "Bleed from Within",
      "After the Burial",
      "All Shall Perish",
      "Chelsea Grin",
      "The Crimson Armada",
      "Oceano",
      "Upon a Burning Body",
      "Born of Osiris",
      "Veil of Maya",
      "Whitechapel",
      "Bring Me the Horizon",
      "Despised Icon",
      "Suicide Silence",
      "Job for a Cowboy"
    ), listOf(
      "AURAS",
      "Coat of Arms",
      "CHON",
      "Elitist",
      "Forever Orion",
      "Volumes",
      "Poryphia",
      "Periphery"
    )
  )

  //Programリストの初期化
  val programs = ArrayList<Program>()

  //開始時刻の年月日を取得（一枠分）
  val startAt1 = LocalDateTime.of(2019, 6, 1, 0, 0).toEpochSecond(ZoneOffset.UTC) * 1000
  val startAt2 = LocalDateTime.of(2019, 6, 1, 1, 0).toEpochSecond(ZoneOffset.UTC) * 1000

  //20分(Long型)
  val min20 = TimeUnit.MINUTES.toMillis(60)

  lists.forEachIndexed { i, bandNames ->
    //リストに対して拡張関数呼び出し
    bandNames.toPrograms(
      //偶数のインデックスであればstartAt1, 奇数であればstartAt2を渡す
      if (i % 2 == 0) startAt1 else startAt2,
      //リストの数が8個なら2枠にして渡す
      if (bandNames.size == 8) min20 * 2 else min20,
      //リストの数が8個なら2枠にして渡す
      if (bandNames.size == 8) min20 * 2 else min20,
      i
    )
      .let(programs::addAll)
  }
  return programs
}