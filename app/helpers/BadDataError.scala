package helpers

class BadDataError(message:String) extends Throwable {
  override def getMessage: String = message
}
