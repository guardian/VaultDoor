package helpers

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import com.sksamuel.elastic4s.http.{ElasticClient, ElasticProperties}


@Singleton
class ESClientManager @Inject()(config:Configuration) {
  val esHost:String = config.get[String]("elasticsearch.hostname")
  val esPort:Int = config.get[Int]("elasticsearch.port")
  val sslFlag:Boolean = config.getOptional[Boolean]("elasticsearch.ssl").getOrElse(false)
  def getClient() = ElasticClient(ElasticProperties(s"elasticsearch://$esHost:$esPort?ssl=$sslFlag"))
}