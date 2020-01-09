package responses

case class FrontendConfigResponse (status:String, projectLockerBaseUrl: String, plutoBaseUrl:Option[String])