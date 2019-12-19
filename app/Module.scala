import actors.{Audit, ObjectCache}
import com.google.inject.AbstractModule
import helpers.UserInfoCache
import models.{AuditRecordDAO, AuditRecordDAOElastic, AuditRecordDAOMongo, AuditRecordDAONull}
import org.slf4j.LoggerFactory
import play.api.libs.concurrent.AkkaGuiceSupport

class Module extends AbstractModule with AkkaGuiceSupport {
  private val logger = LoggerFactory.getLogger(getClass)

  override def configure(): Unit = {
    bind(classOf[UserInfoCache]).asEagerSingleton()
    sys.props.get("audit") match {
      case Some("elasticsearch") | None =>
        bind(classOf[AuditRecordDAO]).to(classOf[AuditRecordDAOElastic])
      case Some("mongo") =>
        bind(classOf[AuditRecordDAO]).to(classOf[AuditRecordDAOMongo])
      case Some("none") =>
        bind(classOf[AuditRecordDAO]).to(classOf[AuditRecordDAONull])
    }

    bindActor[ObjectCache]("object-cache")
    bindActor[Audit]("audit-actor")
  }
}
