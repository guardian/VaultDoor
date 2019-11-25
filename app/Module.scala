import actors.{Audit, ObjectCache}
import com.google.inject.AbstractModule
import helpers.UserInfoCache
import models.{AuditRecordDAO, AuditRecordDAOElastic, AuditRecordDAOMongo}
import org.slf4j.LoggerFactory
import play.api.libs.concurrent.AkkaGuiceSupport

class Module extends AbstractModule with AkkaGuiceSupport {
  private val logger = LoggerFactory.getLogger(getClass)

  override def configure(): Unit = {
    bind(classOf[UserInfoCache]).asEagerSingleton()
    bind(classOf[AuditRecordDAO]).to(classOf[AuditRecordDAOElastic])
    bindActor[ObjectCache]("object-cache")
    bindActor[Audit]("audit-actor")
  }
}
