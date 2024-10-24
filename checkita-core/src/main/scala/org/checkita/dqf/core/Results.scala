package org.checkita.dqf.core

import enumeratum.{Enum, EnumEntry}
import org.json4s.jackson.Serialization.write
import org.checkita.dqf.appsettings.AppSettings
import org.checkita.dqf.core.metrics.ErrorCollection.MetricErrors
import org.checkita.dqf.storage.Models._
import org.checkita.dqf.utils.Common.{getStringHash, jsonFormats}

import scala.collection.immutable

object Results {
  
  /**
   * Enumeration holding all possible result types:
   *   - source metric results
   *   - composed metric results
   *   - load check results
   *   - check results
   */
  sealed trait ResultType extends EnumEntry

  object ResultType extends Enum[ResultType] {
    case object RegularMetric extends ResultType
    case object ComposedMetric extends ResultType
    case object TrendMetric extends ResultType
    case object LoadCheck extends ResultType
    case object Check extends ResultType

    override def values: immutable.IndexedSeq[ResultType] = findValues
  }

  sealed abstract class TypedResult {
    val resultType: ResultType
  }

  /**
   * Metric calculator result.
   *
   * @param metricId          Metric ID
   * @param metricName        Metric calculator name
   * @param result            Metric calculation results
   * @param additionalResult  Additional metric calculation result
   * @param sourceIds         Source IDs on which metric was calculated
   * @param columns           Sequence of metric columns
   * @param errors            Metric errors
   * @param resultType        Type of result
   */
  final case class MetricCalculatorResult(
                                           metricId: String,
                                           metricName: String,
                                           result: Double,
                                           additionalResult: Option[String],
                                           sourceIds: Seq[String],
                                           sourceKeyFields: Seq[String],
                                           columns: Seq[String],
                                           errors: Option[MetricErrors],
                                           resultType: ResultType
                                         ) extends TypedResult {

    /**
     * Converts regular metric calculator result to final regular metric result representation suitable
     * for storing into results storage and sending via targets.
     *
     * @param description Regular metric description
     * @param params      Regular metric parameters (JSON string)
     * @param metadata    Metadata parameters specific to this regular metric (JSON List string)
     * @param jobId       Implicit Job ID
     * @param settings    Implicit application settings object
     * @return Finalized regular metric result
     */
    def finalizeAsRegular(description: Option[String],
                          params: Option[String],
                          metadata: Option[String])(implicit jobId: String,
                                                    settings: AppSettings): ResultMetricRegular =
      ResultMetricRegular(
        jobId,
        metricId,
        metricName,
        description,
        metadata,
        write(sourceIds),
        Some(write(columns)),
        params,
        result,
        additionalResult,
        settings.referenceDateTime.getUtcTS,
        settings.executionDateTime.getUtcTS
      )

    /**
     * Converts composed metric calculator result to final composed metric result representation suitable
     * for storing into results storage and sending via targets.
     *
     * @param description Composed metric description
     * @param formula     Composed metric formula
     * @param metadata    Metadata parameters specific to this composed metric (JSON List string)
     * @param jobId       Implicit Job ID
     * @param settings    Implicit application settings object
     * @return Finalized composed metric result
     */
    def finalizeAsComposed(description: Option[String],
                           formula: String,
                           metadata: Option[String])(implicit jobId: String,
                                                     settings: AppSettings): ResultMetricComposed =
      ResultMetricComposed(
        jobId,
        metricId,
        metricName,
        description,
        metadata,
        write(sourceIds),
        formula,
        result,
        additionalResult,
        settings.referenceDateTime.getUtcTS,
        settings.executionDateTime.getUtcTS
      )

    /**
     * Converts trend metric calculator result to final trend metric result representation suitable
     * for storing into results storage and sending via targets.
     *
     * @param description Regular metric description
     * @param params      Regular metric parameters (JSON string)
     * @param metadata    Metadata parameters specific to this regular metric (JSON List string)
     * @param jobId       Implicit Job ID
     * @param settings    Implicit application settings object
     * @return Finalized trend metric result
     */
    def finalizeAsTrend(description: Option[String],
                        params: Option[String],
                        metadata: Option[String])(implicit jobId: String,
                                                  settings: AppSettings): ResultMetricTrend =
      ResultMetricTrend(
        jobId,
        metricId,
        metricName,
        description,
        metadata,
        write(sourceIds),
        params,
        result,
        additionalResult,
        settings.referenceDateTime.getUtcTS,
        settings.executionDateTime.getUtcTS
      )
      
    /**
     * Retrieves sequence of finalized metric errors from metric calculator result
     * that is suitable for storing into results storage and sending via targets.
     *
     * @param jobId    Implicit Job ID
     * @param settings Implicit application settings object
     * @return Sequence of finalized metric errors
     */
    def finalizeMetricErrors(implicit jobId: String,
                             settings: AppSettings): Seq[ResultMetricError] =
      errors.toSeq.flatMap{ err => 
        err.errors.map { e =>

          val status = e.status.toString
          val message = e.message
          val rowData = write(err.columns.zip(e.rowData).toMap)
          val errorHash = getStringHash(metricId + status + message + rowData)

          ResultMetricError(
            jobId,
            metricId,
            write(sourceIds),
            write(sourceKeyFields),
            write(columns),
            status,
            message,
            rowData,
            errorHash,
            settings.referenceDateTime.getUtcTS,
            settings.executionDateTime.getUtcTS
          )
        }
      }
  }

  /**
   * Check calculator result.
   *
   * @param checkId           Check ID
   * @param checkName         Check calculator name
   * @param baseMetric        Base metric used to build check
   * @param comparedMetric    Sequence of metrics to compare with.
   * @param comparedThreshold Threshold to compare with
   * @param lowerBound        Allowed lower bound for base metric value
   * @param upperBound        Allowed upper bound for base metric value
   * @param status            Check status
   * @param message           Check message
   * @param resultType        Type of result
   *                          
   * @note Expression checks can utilize multiple metrics. Therefore, compareMetric field is represented as
   *       sequence: when compare metric is absent the empty sequence will be written, 
   *       otherwise sequence containing one or more metric IDs will be written.
   */
  final case class CheckCalculatorResult(
                                          checkId: String,
                                          checkName: String,
                                          sourceIds: Seq[String],
                                          baseMetric: String,
                                          comparedMetric: Seq[String],
                                          comparedThreshold: Option[Double],
                                          lowerBound: Option[Double],
                                          upperBound: Option[Double],
                                          status: CalculatorStatus,
                                          message: String,
                                          resultType: ResultType = ResultType.Check
                                        ) extends TypedResult {

    /**
     * Converts check calculator result to final check result representation suitable
     * for storing into results storage and sending via targets.
     *
     * @param description Check description
     * @param metadata    Metadata parameters specific to this check (JSON List string)
     * @param jobId       Implicit Job ID
     * @param settings    Implicit application settings object
     * @return Finalized check result
     */
    def finalize(description: Option[String],
                 metadata: Option[String],
                 isCritical: Boolean)(implicit jobId: String, settings: AppSettings): ResultCheck =
      ResultCheck(
        jobId,
        checkId,
        checkName,
        description,
        metadata,
        write(sourceIds),
        baseMetric,
        Some(write(comparedMetric)),
        comparedThreshold,
        lowerBound,
        upperBound,
        status.toString,
        Some(message),
        isCritical,
        settings.referenceDateTime.getUtcTS,
        settings.executionDateTime.getUtcTS
      )
  }

  /**
   * Load check calculator result
   *
   * @param checkId    Check ID
   * @param checkName  Load check calculator name
   * @param sourceId   Source ID
   * @param expected   Expected value
   * @param status     Check status
   * @param message    Check message
   * @param resultType Type of result
   */
  final case class LoadCheckCalculatorResult(
                                              checkId: String,
                                              checkName: String,
                                              sourceId: String,
                                              expected: String,
                                              status: CalculatorStatus,
                                              message: String,
                                              resultType: ResultType = ResultType.LoadCheck
                                            ) {
    /**
     * Converts load check calculator result to final load check result representation suitable
     * for storing into results storage and sending via targets.
     *
     * @param description Load check description
     * @param metadata    Metadata parameters specific to this load check (JSON List string)
     * @param jobId       Implicit Job ID
     * @param settings    Implicit application settings object
     * @return Finalized load check result
     */
    def finalize(description: Option[String],
                 metadata: Option[String],
                 isCritical: Boolean)(implicit jobId: String, settings: AppSettings): ResultCheckLoad =
      ResultCheckLoad(
        jobId,
        checkId,
        checkName,
        description,
        metadata,
        sourceId,
        expected,
        status.toString,
        Some(message),
        isCritical,
        settings.referenceDateTime.getUtcTS,
        settings.executionDateTime.getUtcTS
      )
  }
}
