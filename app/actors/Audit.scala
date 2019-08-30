package actors

import akka.actor.{Actor, ActorSystem}
import helpers.RangeHeader
import javax.inject.{Inject, Singleton}
import models.{AuditEvent, AuditFile, AuditRecord, AuditRecordDAO}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}


object Audit {
  sealed trait AuditMsg

  /*
  public messages that we expect to receive
   */
  /**
    * log the given event as having occurred. An akka.actor.Status.Success or Failure message will be sent back
    * @param event event to log, as a value of [[AuditEvent]]
    * @param username user that performed the action
    * @param file optional [[AuditFile]] instance indicating the file that was affected
    */
  case class LogEvent(event:AuditEvent.Value, username:String, file:Option[AuditFile], maybeRange:Seq[RangeHeader], notes:Option[String]=None)
}

@Singleton
class Audit @Inject() (auditRecordDAO: AuditRecordDAO, actorSystem:ActorSystem) extends Actor{
  import Audit._
  private implicit val ec:ExecutionContext = actorSystem.dispatcher

  private val logger = LoggerFactory.getLogger(getClass)

  override def receive: Receive = {

    //log the given event as having occurred
    case LogEvent(event,username,file, maybeRange, maybeNotes)=>
      val originalSender = sender()
      val rec = AuditRecord(event, username,file, maybeRange, maybeNotes)
      auditRecordDAO.insert(rec).onComplete({
        case Success(Some(_))=>
          logger.debug(s"recorded event $rec")
          originalSender ! akka.actor.Status.Success
        case Success(None)=>
          logger.error(s"Success reported for $rec but nothing written to database!")
          originalSender ! akka.actor.Status.Failure(new RuntimeException("Nothing written to database"))
        case Failure(err)=>
          logger.error(s"Could not record audit log: ", err)
          originalSender ! akka.actor.Status.Failure(err)
      })
  }
}
