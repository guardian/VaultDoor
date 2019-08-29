package controllers

import helpers.ZonedDateTimeEncoder
import javax.inject.{Inject, Singleton}
import models.{AuditEventEncoder, AuditRecordDAO}
import play.api.libs.circe.Circe
import play.api.mvc.{AbstractController, ControllerComponents}
import io.circe.generic.auto._
import io.circe.syntax._
import org.slf4j.LoggerFactory
import responses.{GenericErrorResponse, ObjectListResponse}

import scala.concurrent.ExecutionContext.Implicits.global
@Singleton
class AuditController @Inject() (auditRecordDAO: AuditRecordDAO, cc:ControllerComponents) extends AbstractController(cc)
  with Circe with ZonedDateTimeEncoder with AuditEventEncoder {

  private val logger = LoggerFactory.getLogger(getClass)

  def getAll(limit:Option[Int]) = Action.async {
    auditRecordDAO.scan(limit.getOrElse(100)).map(records=>
      Ok(ObjectListResponse("ok",records,None).asJson)
    ).recover({
      case err:Throwable=>
        logger.error("Could not retrieve audit records: ", err)
        InternalServerError(GenericErrorResponse("db_error", err.toString).asJson)
    })
  }
}
