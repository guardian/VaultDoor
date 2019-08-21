import actors.ObjectCache
import com.google.inject.AbstractModule
import helpers.UserInfoCache
import org.slf4j.LoggerFactory
import play.api.libs.concurrent.AkkaGuiceSupport

class Module extends AbstractModule with AkkaGuiceSupport {
  private val logger = LoggerFactory.getLogger(getClass)

  override def configure(): Unit = {
    bind(classOf[UserInfoCache]).asEagerSingleton()
    bindActor[ObjectCache]("object-cache")
  }
}
