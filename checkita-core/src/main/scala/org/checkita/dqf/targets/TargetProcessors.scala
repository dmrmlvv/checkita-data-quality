package org.checkita.dqf.targets

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.checkita.dqf.appsettings.AppSettings
import org.checkita.dqf.config.jobconf.Outputs.OutputConfig
import org.checkita.dqf.config.jobconf.Targets._
import org.checkita.dqf.connections.DQConnection
import org.checkita.dqf.storage.Models.ResultSet
import org.checkita.dqf.targets.TargetTypes._
import org.checkita.dqf.targets.builders._
import org.checkita.dqf.targets.builders.dataframe._
import org.checkita.dqf.targets.builders.json.{ErrorJsonBuilder, ResultJsonBuilder, SummaryJsonBuilder}
import org.checkita.dqf.targets.builders.notification.{CheckAlertNotificationBuilder, SummaryNotificationBuilder}
import org.checkita.dqf.utils.ResultUtils._
import org.checkita.dqf.writers._

object TargetProcessors {
  
  trait TargetProcessor[T <: TargetConfig with OutputConfig, R] {
    this: TargetBuilder[T, R] with OutputWriter[R, T] =>

    /**
     * Safely process target given the target configuration.
     * @param target Target configuration
     * @param results All job results
     * @return "Success" string in case of successful operation or a list of errors.
     */
    def process(target: T,
                results: ResultSet)(implicit jobId: String,
                                    settings: AppSettings,
                                    spark: SparkSession,
                                    connections: Map[String, DQConnection]): Result[String] =
      build(target, results).flatMap(r => write(target, r))
  }
  
  implicit object ResultFileTargetProcessor
    extends TargetProcessor[ResultFileTargetConfig, DataFrame]
      with ResultDataFrameBuilder[ResultFileTargetConfig]
      with FileWriter[ResultFileTargetConfig] with ResultTargetType

  implicit object ResultHiveTargetProcessor
    extends TargetProcessor[ResultHiveTargetConfig, DataFrame]
      with ResultDataFrameBuilder[ResultHiveTargetConfig]
      with HiveWriter[ResultHiveTargetConfig] with ResultTargetType

  implicit object ResultKafkaTargetProcessor
    extends TargetProcessor[ResultKafkaTargetConfig, Seq[String]]
      with ResultJsonBuilder[ResultKafkaTargetConfig]
      with KafkaWriter[ResultKafkaTargetConfig] with ResultTargetType

  implicit object ErrorCollFileTargetProcessor
    extends TargetProcessor[ErrorCollFileTargetConfig, DataFrame]
      with ErrorDataFrameBuilder[ErrorCollFileTargetConfig]
      with FileWriter[ErrorCollFileTargetConfig] with MetricErrorsTargetType

  implicit object ErrorCollHiveTargetProcessor
    extends TargetProcessor[ErrorCollHiveTargetConfig, DataFrame]
      with ErrorDataFrameBuilder[ErrorCollHiveTargetConfig]
      with HiveWriter[ErrorCollHiveTargetConfig] with MetricErrorsTargetType

  implicit object ErrorCollKafkaTargetProcessor
    extends TargetProcessor[ErrorCollKafkaTargetConfig, Seq[String]]
      with ErrorJsonBuilder[ErrorCollKafkaTargetConfig]
      with KafkaWriter[ErrorCollKafkaTargetConfig] with MetricErrorsTargetType

  implicit object SummaryEmailTargetProcessor
    extends TargetProcessor[SummaryEmailTargetConfig, NotificationMessage]
      with SummaryNotificationBuilder[SummaryEmailTargetConfig]
      with EmailWriter[SummaryEmailTargetConfig] with SummaryTargetType

  implicit object SummaryMattermostTargetProcessor
    extends TargetProcessor[SummaryMattermostTargetConfig, NotificationMessage]
      with SummaryNotificationBuilder[SummaryMattermostTargetConfig]
      with MMWriter[SummaryMattermostTargetConfig] with SummaryTargetType

  implicit object SummaryKafkaTargetProcessor
    extends TargetProcessor[SummaryKafkaTargetConfig, Seq[String]]
      with SummaryJsonBuilder[SummaryKafkaTargetConfig]
      with KafkaWriter[SummaryKafkaTargetConfig] with SummaryTargetType

  implicit object CheckAlertEmailTargetProcessor
    extends TargetProcessor[CheckAlertEmailTargetConfig, NotificationMessage]
      with CheckAlertNotificationBuilder[CheckAlertEmailTargetConfig]
      with EmailWriter[CheckAlertEmailTargetConfig] with SummaryTargetType

  implicit object CheckAlertMattermostTargetProcessor
    extends TargetProcessor[CheckAlertMattermostTargetConfig, NotificationMessage]
      with CheckAlertNotificationBuilder[CheckAlertMattermostTargetConfig]
      with MMWriter[CheckAlertMattermostTargetConfig] with SummaryTargetType
      
  implicit object AnyTargetProcessor{
    def process(target: TargetConfig,
                results: ResultSet)(implicit jobId: String,
                                    settings: AppSettings,
                                    spark: SparkSession,
                                    connections: Map[String, DQConnection]): Result[String] = target match {
      case resFile: ResultFileTargetConfig => ResultFileTargetProcessor.process(resFile, results)
      case resHive: ResultHiveTargetConfig => ResultHiveTargetProcessor.process(resHive, results)
      case resKafka: ResultKafkaTargetConfig => ResultKafkaTargetProcessor.process(resKafka, results)
      case errFile: ErrorCollFileTargetConfig => ErrorCollFileTargetProcessor.process(errFile, results)
      case errHive: ErrorCollHiveTargetConfig => ErrorCollHiveTargetProcessor.process(errHive, results)
      case errKafka: ErrorCollKafkaTargetConfig => ErrorCollKafkaTargetProcessor.process(errKafka, results)
      case sumEmail: SummaryEmailTargetConfig => SummaryEmailTargetProcessor.process(sumEmail, results)
      case sumMM: SummaryMattermostTargetConfig => SummaryMattermostTargetProcessor.process(sumMM, results)
      case sumKafka: SummaryKafkaTargetConfig => SummaryKafkaTargetProcessor.process(sumKafka, results)
      case chkEmail: CheckAlertEmailTargetConfig => CheckAlertEmailTargetProcessor.process(chkEmail, results)
      case chkMM: CheckAlertMattermostTargetConfig => CheckAlertMattermostTargetProcessor.process(chkMM, results)
    }
  }
  
  
  implicit class TargetProcessingOps(target: TargetConfig)(implicit jobId: String,
                                                           settings: AppSettings,
                                                           spark: SparkSession,
                                                           connections: Map[String, DQConnection]) {
  
    def process(results: ResultSet): Result[String] = AnyTargetProcessor.process(target, results)
  }
}
