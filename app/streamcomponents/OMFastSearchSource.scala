package streamcomponents

import com.om.mxs.client.japi.{SearchTerm, UserInfo}

class OMFastSearchSource(userInfo:UserInfo, searchTerms:Array[SearchTerm], includeFields:Array[String], atOnce:Int=100) extends OMFastSearchSourceBase(userInfo, atOnce) {
  override def getSearchTerms: SearchTerm = SearchTerm.createANDTerm(searchTerms ++ includeFields.map(field=>SearchTerm.createSimpleTerm("__mxs__rtn_attr", field)))
}
