package responses

case class GenericObjectResponse[T](status:String, itemClass:String, entry:T)
