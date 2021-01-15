package streamcomponents

import com.om.mxs.client.japi.{Constants, SearchTerm, UserInfo}

class OMFastContentSearchSource(userInfo:UserInfo, contentSearchString:String, atOnce:Int=100) extends OMFastSearchSourceBase(userInfo, atOnce) {
  override def getSearchTerms: SearchTerm = SearchTerm.createSimpleTerm(Constants.CONTENT, contentSearchString)
}
