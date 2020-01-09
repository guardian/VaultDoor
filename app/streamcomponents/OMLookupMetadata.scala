package streamcomponents

import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, Attributes, FlowShape, Inlet, Materializer, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import com.om.mxs.client.internal.TaggedIOException
import com.om.mxs.client.japi.{MXFSFileAttributes, MatrixStore, MxsObject, UserInfo, Vault}
import helpers.MetadataHelper
import models.{FileAttributes, MxsMetadata, ObjectMatrixEntry}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

/**
  * look up metadata for the given objectmatrix entry
  * @param userInfo UserInfo instance giving the appliance to connect to
  */
class OMLookupMetadata(userInfo:UserInfo, ignoreOnLocked:Boolean=true) extends GraphStage[FlowShape[ObjectMatrixEntry,ObjectMatrixEntry]] {
  private final val in:Inlet[ObjectMatrixEntry] = Inlet.create("OMLookupMetadata.in")
  private final val out:Outlet[ObjectMatrixEntry] = Outlet.create("OMLookupMetadata.out")

  override def shape: FlowShape[ObjectMatrixEntry, ObjectMatrixEntry] = FlowShape.of(in,out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)
    private var vault:Option[Vault] = None

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem=grab(in)

        def doLookup():Unit = {
          try {
            val obj = vault.get.getObject(elem.oid)

            val meta = MetadataHelper.getAttributeMetadataSync(obj)
            val updated = elem.copy(attributes = Some(meta), fileAttribues = Some(FileAttributes(MetadataHelper.getMxfsMetadata(obj))))
            push(out, updated)
          } catch {
            case err:java.io.IOException=>
              if(err.getMessage.contains("error 311")){
                logger.error(s"OMLookupMetadata got 'unable to lock object' on ${elem.oid}, retrying")
                if(ignoreOnLocked){
                  pull(in)
                } else {
                  Thread.sleep(1000)
                  doLookup()
                }
              } else {
                logger.error(s"Could not look up object metadata: ", err)
                failStage(err)
              }
            case err:TaggedIOException=>
              if(err.getError==311){
                logger.error(s"OMLookupMetadata got 'unable to lock object' on ${elem.oid}, retrying")
                Thread.sleep(1000)
                doLookup()
              } else {
                logger.error(s"Could not look up object metadata: ", err)
                failStage(err)
              }
            case err: Throwable =>
              logger.error(s"Could not look up object metadata: ", err)
              failStage(err)
          }
        }
        doLookup()
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })

    override def preStart(): Unit = {
      vault = Some(MatrixStore.openVault(userInfo))
    }

    override def postStop(): Unit = {
      logger.info("Stream terminated")
      vault.map(_.dispose())
    }
  }
}
