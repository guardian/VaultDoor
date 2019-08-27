package helpers

/**
  * extension method for scala.util.random to filter next alphanumeric character
  */
trait RandomExtender {
  def nextAlphaChar(r:scala.util.Random):Char = {
    var ch:Char = ';'
    do {
      ch = r.nextPrintableChar()
    } while(! ( (ch>='a' && ch<='z') || (ch>='A' && ch<='Z') ))
    ch
  }
}

object RandomExtender {
  implicit val extender: RandomExtender = new RandomExtender {}
  implicit class charExtenderOps(value:scala.util.Random) {
    def nextAlphaChar(implicit ex:RandomExtender):Char = ex.nextAlphaChar(value)
  }
}

