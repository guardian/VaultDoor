package streamcomponents

import akka.actor.ActorRef
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString
import models.{AuditEvent, AuditFile}
import org.slf4j.LoggerFactory

class AuditLogFinish (auditLogActor:ActorRef, auditFile:AuditFile, uid:String, expectedBytes:Long) extends GraphStage[FlowShape[ByteString,ByteString]] {
  final val in:Inlet[ByteString] = Inlet.create("AuditLogFinish.in")
  final val out:Outlet[ByteString] = Outlet.create("AuditLogFinish.out")

  override def shape: FlowShape[ByteString, ByteString] = FlowShape.of(in,out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) with OutHandler with InHandler  {
    private val logger = LoggerFactory.getLogger(getClass)
    private var bytesCounter:Long = 0

    override def onPush(): Unit = {
      val elem=grab(in)
      bytesCounter+=elem.length
      push(out, elem)
    }

    override def onPull(): Unit = pull(in)

    override def preStart(): Unit = {
      logger.info(s"Streaming started for $auditFile")
      auditLogActor ! actors.Audit.LogEvent(AuditEvent.STREAMOUT_START, uid, Some(auditFile), Seq(), None, None)
    }

    override def postStop(): Unit = {
      logger.info(s"Streaming finished for $auditFile, passed $bytesCounter bytes")
      if(bytesCounter==expectedBytes) {
        auditLogActor ! actors.Audit.LogEvent(AuditEvent.STREAMOUT_END, uid, Some(auditFile), Seq(), None, Some(bytesCounter))
      } else {
        val maybePct = if(expectedBytes>0) Some((bytesCounter.toDouble / expectedBytes.toDouble)*100) else None
        val maybePctString = maybePct.map(pct=>f"$pct%3.0f")
        auditLogActor ! actors.Audit.LogEvent(AuditEvent.STREAMOUT_SHORT, uid, Some(auditFile), Seq(), Some(s"Expected $expectedBytes bytes, streamed ${maybePctString.getOrElse("(unknown)")}% of file"), Some(bytesCounter))
      }
    }

    override def onUpstreamFailure(ex: Throwable): Unit = {
      logger.error(s"Streaming failed for $auditFile on $bytesCounter bytes: ", ex)
      auditLogActor ! actors.Audit.LogEvent(AuditEvent.OMERROR,uid,Some(auditFile),Seq(),None,Some(bytesCounter))
    }

    override def onDownstreamFinish(): Unit = super.onDownstreamFinish()

    setHandlers(in,out, this)
  }
}
