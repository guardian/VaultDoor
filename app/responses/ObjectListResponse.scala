package responses

case class ObjectListResponse[T] (status:String, entries: Seq[T], totalCount: Option[Long])
