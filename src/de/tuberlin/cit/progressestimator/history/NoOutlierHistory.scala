package de.tuberlin.cit.progressestimator.history

import de.tuberlin.cit.progressestimator.entity.{ Iteration, JobExecution, DstatEntry }
import java.rmi._
import java.rmi.server._
import java.time.Instant
import de.tuberlin.cit.progressestimator.history.db.DB
import java.util.Date
import java.text.SimpleDateFormat
import scala.util.matching.Regex
import scala.collection.mutable.ListBuffer
import com.typesafe.config.ConfigFactory
import java.sql.{ Connection, DriverManager }
import anorm.SqlParser._
import anorm._
import de.tuberlin.cit.progressestimator.entity.JobExecution
import de.tuberlin.cit.progressestimator.entity.Iteration
import scala.language.postfixOps
import de.tuberlin.cit.progressestimator.entity.DstatEntries
import com.google.inject.Singleton
import com.google.inject.Inject
import de.tuberlin.cit.progressestimator.estimator.EstimatorConfig
@Singleton /**
 * This job history is a wrapper removing outliers.
 * 
 */
class NoOutlierHistory(val historyId: String) extends JobHistory {
  private val wrappedHistory: JobHistory = new UnionJobHistory()
  private val outliers: Array[Integer] = getOutlierIds()
  private val thesisIds: Array[Integer] = getThesisIds()

  /**
   * see [[de.tuberlin.cit.progressestimator.history.JobHistory]]
   */
  def getHistoryId(): String = historyId

  /**
   * Returns the ids of job executions which are considered an outlier.
   */
  private def getOutlierIds(): Array[Integer] = {

    if (historyId.equals("Thesis")) {
      new Array[Integer](0)
    } else {

      val allJobs = wrappedHistory.getJobExecutions()
      val removeFactor = 2
      var outliers = if (historyId.contains("NoOutliers")) {
        allJobs.groupBy(j => (j.name, j.avgNumberOfNodes, j.input.name, j.parameters, j.expName)).flatMap(t => {
          val jobs = t._2.sortBy(_.runtime)
          if (jobs.length > 3) {
            val runtimes = jobs.map(_.runtime)
            val avgRuntime = runtimes.sum / jobs.length
            val medianRuntime =
              if (runtimes.size % 2 == 1) runtimes(runtimes.size / 2)
              else {
                val (up, down) = runtimes.splitAt(runtimes.size / 2)
                (up.last + down.head) / 2
              }
            val outliers = jobs.filter(j =>
              j.runtime > medianRuntime * removeFactor || j.runtime < medianRuntime / removeFactor)
            outliers.map(_.id)
          } else {
            ListBuffer[Integer]()
          }
        })
      } else {
        ListBuffer[Integer]()
      }

      (outliers).toArray

    }
  }

  /**
   * returns true if the job executions with the given id is considered an outlier.
   */
  private def isOutlier(id: Integer): Boolean = {
    val thesisHistory = historyId.equals("Thesis")

    (thesisHistory && !thesisIds.contains(id)) || (!thesisHistory && outliers.contains(id))
  }

  /**
   * see [[de.tuberlin.cit.progressestimator.history.JobHistory]]
   */
  override def getJobExecutions(): ListBuffer[JobExecution] = {
    wrappedHistory.getJobExecutions().filter(j => !isOutlier(j.id))
  }

  /**
   * returns all job executions in the history, including outliers.
   * This may not be used for the estimation, but for information,
   * for example for a complete list of executions.
   */
  def getJobExecutionsIncludingOutlier(): ListBuffer[JobExecution] = {
    wrappedHistory.getJobExecutions().map(j => {
      if (isOutlier(j.id)) {
        j.outlier = true
      }
      j
    })
  }

  /**
   * see [[de.tuberlin.cit.progressestimator.history.JobHistory]]
   */
  override def getJobExecutions(name: String): ListBuffer[JobExecution] = {
    wrappedHistory.getJobExecutions(name).filter(j => !isOutlier(j.id))
  }

  /**
   * see [[de.tuberlin.cit.progressestimator.history.JobHistory]]
   */
  override def getJobExecutionById(id: Int): JobExecution = {
    if (isOutlier(id)) {
      null
    } else {
      wrappedHistory.getJobExecutionById(id)
    }
  }

  /**
   * see [[de.tuberlin.cit.progressestimator.history.JobHistory]]
   */
  override def getDstatEntries(id: Int): DstatEntries = {
    if (isOutlier(id)) {
      null
    } else {
      wrappedHistory.getDstatEntries(id)
    }
  }

  /**
   * see [[de.tuberlin.cit.progressestimator.history.JobHistory]]
   */
  override def getDstatAvg(id: Int, property: String): List[(String, Double)] = {
    if (isOutlier(id)) {
      null
    } else {
      wrappedHistory.getDstatAvg(id, property)
    }
  }

  /**
   * see [[de.tuberlin.cit.progressestimator.history.JobHistory]]
   */
  override def getDstatAvg(id: Int, property: String, time: Instant): List[(String, Double)] = {
    if (isOutlier(id)) {
      null
    } else {
      wrappedHistory.getDstatAvg(id, property, time)
    }
  }

  /**
   * see [[de.tuberlin.cit.progressestimator.history.JobHistory]]
   */
  override def getDstatAvg(id: Int, property: String, timeStart: Instant, timeEnd: Instant): List[(String, Double)] = {
    if (isOutlier(id)) {
      null
    } else {
      wrappedHistory.getDstatAvg(id, property, timeStart, timeEnd)
    }
  }

  /**
   * see [[de.tuberlin.cit.progressestimator.history.JobHistory]]
   */
  override def getDstatAvgOfValueExpression(id: Int, property1: String, property2: String, operator: String, timeStart: Instant, timeEnd: Instant): List[(String, Double)] = {
    if (isOutlier(id)) {
      null
    } else {
      wrappedHistory.getDstatAvgOfValueExpression(id, property1, property2, operator, timeStart, timeEnd)
    }
  }

  private def getThesisIds(): Array[Integer] = {
    val ids: ListBuffer[Integer] = ListBuffer(
      1497525320,
      1497524934,
      1497524822,
      1497524778,
      1497524645,
      1497524598,
      1497524550,
      1497524490,
      1497524234,
      1497524175,
      1497524082,
      1497523967,
      1497523883,
      1497523783,
      1497523743,
      1497523703,
      1497523663,
      1497523491,
      1497523196,
      1497523149,
      1497523100,
      1497523053,
      1497522999,
      1497522960,
      1497522921,
      1497522867,
      1497522366,
      1497522318,
      1497522274,
      1497522224,
      1497522135,
      1497522053,
      1497521979,
      1497521913,
      1497521841,
      1497521519,
      1497521448,
      1497521402,
      1497521351,
      1497521303,
      1497521253,
      1497521097,
      1497520963,
      1497520883,
      1497520479,
      1497520435,
      1497520390,
      1497520348,
      1497520285,
      1497520237,
      1497520188,
      1497520140,
      1497520094,
      1497519666,
      1497519618,
      1497519569,
      1497519519,
      1497519461,
      1497519409,
      1497519357,
      1497519309,
      1497519257,
      1497518827,
      1497518781,
      1497518733,
      1497518686,
      1497518623,
      1497518580,
      1497518528,
      1497518482,
      1497518434,
      1497517952,
      1497517901,
      1497517844,
      1497517790,
      1497517723,
      1497517676,
      1497517628,
      1497517577,
      1497517520,
      1498321007,
      1498320881,
      1498320759,
      1498320639,
      1498320532,
      1498320424,
      1498320325,
      1498320231,
      1498320130,
      1498319741,
      1498319654,
      1498319574,
      1498319478,
      1498319398,
      1498319308,
      1498319230,
      1498319150,
      1498319072,
      1498318692,
      1498318604,
      1498318531,
      1498318455,
      1498318379,
      1498318290,
      1498318215,
      1498318140,
      1498318067,
      1498317911,
      1498317832,
      1498317760,
      1498317683,
      1498317597,
      1498317505,
      1498317423,
      1498317348,
      1498317227,
      1498316810,
      1498316736,
      1498316654,
      1498316583,
      1498316490,
      1498316406,
      1498316320,
      1498316236,
      1498316149,
      1498315751,
      1498315685,
      1498315619,
      1498315552,
      1498315477,
      1498315407,
      1498315338,
      1498315276,
      1498315023,
      1498314875,
      1498314803,
      1498314730,
      1498314660,
      1498314583,
      1498314507,
      1498314440,
      1498314370,
      1498314281,
      1498313920,
      1498313846,
      1498313776,
      1498313707,
      1498313613,
      1498313537,
      1498313461,
      1498313386,
      1498313299,
      1498312903,
      1498312828,
      1498312758,
      1498312684,
      1498312602,
      1498312526,
      1498312448,
      1498312371,
      1498312280,
      1498091409,
      1498091267,
      1498091118,
      1498090932,
      1498090747,
      1498090601,
      1498090153,
      1498089734,
      1498089236,
      1498088958,
      1498088839,
      1498088718,
      1498088568,
      1498088423,
      1498088297,
      1498088145,
      1498087952,
      1498087790,
      1498087548,
      1498087362,
      1498087226,
      1498087080,
      1498086939,
      1498086816,
      1498086673,
      1498086522,
      1498086360,
      1498086150,
      1498086035,
      1498085908,
      1498085777,
      1498085648,
      1498085494,
      1498085341,
      1498085203,
      1498085065,
      1498084869,
      1498084780,
      1498084691,
      1498084577,
      1498084479,
      1498084383,
      1498084278,
      1498084181,
      1498084085,
      1498083898,
      1498083799,
      1498083707,
      1498083591,
      1498083492,
      1498083386,
      1498083269,
      1498083168,
      1498083048,
      1498082845,
      1498082739,
      1498082618,
      1498082486,
      1498082351,
      1498082231,
      1498082100,
      1498081934,
      1498081803,
      1498081571,
      1498081474,
      1498081361,
      1498081222,
      1498081089,
      1498080972,
      1498080843,
      1498080716,
      1498080593,
      1497927400,
      1497927310,
      1497927219,
      1497927129,
      1497927027,
      1497926935,
      1497926841,
      1497926752,
      1497926590,
      1497923654,
      1497923570,
      1497923485,
      1497923407,
      1497923310,
      1497923228,
      1497923147,
      1497923068,
      1497922977,
      1497921217,
      1497921116,
      1497921031,
      1497920946,
      1497920839,
      1497920742,
      1497920662,
      1497920561,
      1497920442,
      1497919260,
      1497919178,
      1497919101,
      1497918992,
      1497918892,
      1497918805,
      1497918575,
      1497918450,
      1497918374,
      1497917767,
      1497917697,
      1497917631,
      1497917564,
      1497917484,
      1497917415,
      1497917347,
      1497917277,
      1497917197,
      1497916588,
      1497916517,
      1497916453,
      1497916391,
      1497916311,
      1497916247,
      1497916187,
      1497916123,
      1497916041,
      1497915433,
      1497915332,
      1497915225,
      1497915114,
      1497914966,
      1497914857,
      1497914759,
      1497914460,
      1497914380,
      1497913631,
      1497913539,
      1497913452,
      1497913064,
      1497912982,
      1497912899,
      1497912784,
      1497912699,
      1497912602,
      1498388781,
      1498388595,
      1498388471,
      1498388341,
      1498388221,
      1498388101,
      1498387868,
      1498387721,
      1498387546,
      1498387421,
      1498387307,
      1498387192,
      1498386978,
      1498386834,
      1498386686,
      1498386555,
      1498386439,
      1498386324,
      1498386116,
      1498385950,
      1498385789,
      1498385598,
      1498385426,
      1498385246,
      1498385032,
      1498384893,
      1498384749,
      1498384579,
      1498384428,
      1498384280,
      1498384071,
      1498383928,
      1498383791,
      1498383632,
      1498383495,
      1498383352,
      1498383146,
      1498383013,
      1498382868,
      1498382702,
      1498382574,
      1498382440,
      1498382224,
      1498382068,
      1498381899,
      1498381754,
      1498381619,
      1498381463,
      1497593535,
      1497593297,
      1497592983,
      1497592714,
      1497592415,
      1497592110,
      1497591237,
      1497590946,
      1497590659,
      1497590375,
      1497590104,
      1497589825,
      1497589528,
      1497589302,
      1497589020,
      1497588757,
      1497588459,
      1497588197,
      1497577801,
      1497577576,
      1497577312,
      1497576401,
      1497576111,
      1497575866,
      1497575541,
      1497575280,
      1497575026,
      1497574775,
      1497574504,
      1497574256,
      1497573439,
      1497573202,
      1497572956,
      1497572700,
      1497572410,
      1497572139,
      1497571294,
      1497571046,
      1497570811,
      1497570576,
      1497570271,
      1497569994,
      1497569074,
      1497568827,
      1497568562,
      1497568304,
      1497567942,
      1497567593,
      1497663657,
      1497663136,
      1497662613,
      1497661095,
      1497660566,
      1497660038,
      1497658531,
      1497658104,
      1497657618,
      1497656290,
      1497655819,
      1497655360,
      1497653852,
      1497653441,
      1497653059,
      1497651826,
      1497651347,
      1497650898,
      1497649406,
      1497648995,
      1497648533,
      1497647186,
      1497646741,
      1497646268,
      1497645070,
      1497644812,
      1497644531,
      1497643649,
      1497643355,
      1497643011,
      1497642035,
      1497641764,
      1497641484,
      1497640579,
      1497640260,
      1497639903,
      1497638738,
      1497638254,
      1497637853,
      1497636707,
      1497636355,
      1497636034,
      1497634494,
      1497634097,
      1497633743,
      1497632493,
      1497632072,
      1497631774,
      1497624456,
      1497623877,
      1497623141,
      1497662271,
      1497661959,
      1497661629,
      1497659704,
      1497659386,
      1497659055,
      1497657338,
      1497657055,
      1497656768,
      1497655060,
      1497654775,
      1497654479,
      1497652794,
      1497652530,
      1497652260,
      1497650576,
      1497650296,
      1497650000,
      1497648254,
      1497647974,
      1497647680,
      1497645997,
      1497645709,
      1497645404,
      1497644334,
      1497644164,
      1497643984,
      1497642773,
      1497642569,
      1497642368,
      1497641300,
      1497641113,
      1497640933,
      1497639669,
      1497639458,
      1497639242,
      1497637580,
      1497637322,
      1497637038,
      1497635844,
      1497635658,
      1497635455,
      1497633480,
      1497633237,
      1497632838,
      1497631256,
      1497630571,
      1497630318,
      1497626054,
      1497625619,
      1497625212,
      1497622756,
      1497622296,
      1497621935,
      1498418465,
      1498417689,
      1498416970,
      1498414781,
      1498414043,
      1498413289,
      1498411000,
      1498410242,
      1498409429,
      1498407102,
      1498406348,
      1498405606,
      1498403246,
      1498402623,
      1498401970,
      1498399893,
      1498399103,
      1498398415,
      1498396446,
      1498395841,
      1498395228,
      1498393335,
      1498392705,
      1498392101,
      1498302677,
      1498302035,
      1498301428,
      1498300750,
      1498300041,
      1498299420,
      1498298834,
      1498298319,
      1498297739,
      1498297201,
      1498296579,
      1498296025,
      1498295436,
      1498294994,
      1498294558,
      1498294116,
      1498293675,
      1498293254,
      1498292731,
      1498292261,
      1498291812,
      1498291317,
      1498290855,
      1498290407,
      1498289982,
      1498289606,
      1498289247,
      1498288796,
      1498288414,
      1498288051,
      1498287574,
      1498287163,
      1498286762,
      1498286385,
      1498286034,
      1498285658,
      1498149624,
      1498149131,
      1498148688,
      1498148205,
      1498147785,
      1498147346,
      1498143957,
      1498143578,
      1498143206,
      1498142757,
      1498142369,
      1498142020,
      1498136477,
      1498136087,
      1498135719,
      1498135350,
      1498135022,
      1498134663,
      1498134214,
      1498133851,
      1498133495,
      1498133159,
      1498132826,
      1498132505,
      1498129990,
      1498129723,
      1498129459,
      1498129194,
      1498128927,
      1498128680,
      1498126763,
      1498126511,
      1498126255,
      1498125987,
      1498125710,
      1498125445,
      1498121814,
      1498121550,
      1498121273,
      1498121018,
      1498120731,
      1498120437,
      1498120015,
      1498119740,
      1498119446,
      1498119124,
      1498118839,
      1498118562,
      1497483361,
      1497483070,
      1497482782,
      1497482455,
      1497482229,
      1497482007,
      1497481672,
      1497481392,
      1497481174,
      1497480843,
      1497480627,
      1497480411,
      1497480123,
      1497479947,
      1497479779,
      1497479497,
      1497479337,
      1497479168,
      1497478838,
      1497478652,
      1497478395,
      1497478092,
      1497477876,
      1497477639,
      1497469239,
      1497469039,
      1497468780,
      1497466664,
      1497466356,
      1497466170,
      1497465977,
      1497465597,
      1497465409,
      1497465223,
      1497464936,
      1497464686,
      1497464474,
      1497464214,
      1497464056,
      1497463900,
      1497463645,
      1497463501,
      1497463356,
      1497463072,
      1497462870,
      1497462701,
      1497462368,
      1497462154,
      1497461932,
      1498341393,
      1498340629,
      1498339884,
      1498333011,
      1498332751,
      1498332485,
      1498332220,
      1498330495,
      1498330254,
      1498330021,
      1498329778,
      1498328191,
      1498327956,
      1498327726,
      1498327499,
      1498325982,
      1498325770,
      1498325553,
      1498325337,
      1498323869,
      1498323662,
      1498323455,
      1498323243,
      1498335796,
      1498335105,
      1498334371,
      1498333643,
      1498331895,
      1498331630,
      1498331355,
      1498331088,
      1498329471,
      1498329232,
      1498328993,
      1498328749,
      1498327217,
      1498326984,
      1498326748,
      1498326525,
      1498325051,
      1498324834,
      1498324615,
      1498324393,
      1498322966,
      1498322756,
      1498322549,
      1498322322,
      1497827192,
      1497827076,
      1497732085,
      1497731875,
      1497731668,
      1497731466,
      1497730946,
      1497730777,
      1497730619,
      1497730463,
      1497730060,
      1497729926,
      1497729798,
      1497729659,
      1497729305,
      1497729171,
      1497729041,
      1497728896,
      1497728559,
      1497728421,
      1497728297,
      1497728178,
      1497727868,
      1497727751,
      1497727638,
      1497727533,
      1497727247,
      1497727145,
      1497727034,
      1497726931,
      1497726651,
      1497726520,
      1497725451,
      1497711043,
      1497710751,
      1497710570,
      1497710451,
      1497710330,
      1498226934,
      1498221516,
      1498215744,
      1498210481,
      1498194474,
      1498191415,
      1498188365,
      1498185407,
      1497903561,
      1497888261,
      1497885420,
      1497882510,
      1497879656,
      1497872346,
      1497870861,
      1497869364,
      1497867896,
      1497861825,
      1497861430,
      1497861011,
      1497860632,
      1497858579,
      1497829085,
      1497828695,
      1498232346,
      1498206834,
      1498203735,
      1498200701,
      1498197651,
      1497899978,
      1497897046,
      1497894110,
      1497891215,
      1497873911,
      1497863523,
      1497863125,
      1497862756,
      1497862338,
      1497859815)

    ids.toArray
  }
}