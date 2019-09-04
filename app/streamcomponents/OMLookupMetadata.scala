package streamcomponents

import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import akka.stream._
import com.om.mxs.client.japi.{MXFSFileAttributes, MatrixStore, UserInfo, Vault}
import helpers.MetadataHelper
import models.{FileAttributes, MxsMetadata, ObjectMatrixEntry}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

/**
  * look up metadata for the given objectmatrix entry
  * @param mat
  * @param ec
  */
class OMLookupMetadata(userInfo:UserInfo)(implicit mat:Materializer, ec:ExecutionContext) extends GraphStage[FlowShape[ObjectMatrixEntry,ObjectMatrixEntry]] {
  private final val in:Inlet[ObjectMatrixEntry] = Inlet.create("OMLookupMetadata.in")
  private final val out:Outlet[ObjectMatrixEntry] = Outlet.create("OMLookupMetadata.out")

  override def shape: FlowShape[ObjectMatrixEntry, ObjectMatrixEntry] = FlowShape.of(in,out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)
    private var vault:Vault = _

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem=grab(in)

        val completeCb = getAsyncCallback[(ObjectMatrixEntry,MxsMetadata,MXFSFileAttributes)](argTuple=>{
          val updated = argTuple._1.copy(
            attributes = Some(argTuple._2),
            fileAttribues = Some(FileAttributes(argTuple._3))
          )
          push(out, updated)
        })

        val failedCb = getAsyncCallback[Throwable](err=>failStage(err))

        try {
          val obj = vault.getObject(elem.oid)

          MetadataHelper.getAttributeMetadata(obj).onComplete({
            case Success(meta)=>
              completeCb.invoke((elem, meta, MetadataHelper.getMxfsMetadata(obj)))
            case Failure(exception)=>
              logger.error(s"Could not look up metadata: ", exception)
              failedCb.invoke(exception)
          })

        } catch {
          case err:Throwable=>
            logger.error(s"Could not look up object metadata: ", err)
            failStage(err)
        }
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })

    override def preStart(): Unit = {
      Try { MatrixStore.openVault(userInfo) } match {
        case Failure(err)=>
          logger.error(s"Could not open vault: ", err)
          failStage(err)
        case Success(v)=>vault=v
      }
    }

    override def postStop(): Unit = {
      vault.dispose()
    }
  }
}
