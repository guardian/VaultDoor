package streamcomponents

import akka.actor.ActorRef
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import akka.util.ByteString
import models.{AuditEvent, AuditFile}
import org.slf4j.LoggerFactory

class AuditLogFinish (auditLogActor:ActorRef, auditFile:AuditFile, uid:String) extends GraphStage[FlowShape[ByteString,ByteString]] {
  private final val in:Inlet[ByteString] = Inlet.create("AuditLogFinish.in")
  private final val out:Outlet[ByteString] = Outlet.create("AuditLogFinish.out")

  override def shape: FlowShape[ByteString, ByteString] = FlowShape.of(in,out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)
    private var bytesCounter:Long = 0

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem=grab(in)
        bytesCounter+=elem.length
        push(out, elem)
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })

    override def preStart(): Unit = {
      logger.info(s"Streaming started for $auditFile")
      auditLogActor ! actors.Audit.LogEvent(AuditEvent.STREAMOUT_START, uid, Some(auditFile), Seq(), None, None)
    }

    override def postStop(): Unit = {
      logger.info(s"Streaming finished for $auditFile, passed $bytesCounter bytes")
      auditLogActor ! actors.Audit.LogEvent(AuditEvent.STREAMOUT_END,uid,Some(auditFile),Seq(),None,Some(bytesCounter))
    }
  }
}
