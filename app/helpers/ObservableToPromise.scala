package helpers

import org.mongodb.scala.Observer

import scala.concurrent.Promise

object ObservableToPromise {
  /**
    * simple implementation of an Observer that completes a Promise on success
    * @param p Promise to be completed
    * @tparam A type of the result expected from the Observer
    * @return the new Observer. this can then be subscribed as per the docs.
    */
  def make[A](p:Promise[Option[A]]) = {
    new Observer[A] {
      private var nextValue:Option[A] = None
      override def onNext(result: A): Unit = nextValue=Some(result)

      override def onComplete(): Unit = p.success(nextValue)

      override def onError(e: Throwable): Unit = p.failure(e)
    }
  }

  /**
    * observer that "folds" incoming results into a Seq and returns them
    * @param p promise that will be completed
    * @tparam A type of data that is coming in and will be buffered and returned
    * @return an Observer that can be subscribed to a fetch() as per the docs
    */
  def makeFolder[A](p:Promise[Seq[A]]) = {
    var finalSeq:Seq[A] = Seq()
    new Observer[A] {
      override def onNext(result: A): Unit = { finalSeq = finalSeq ++ Seq(result)}

      override def onComplete(): Unit = p.success(finalSeq)

      override def onError(e: Throwable): Unit = p.failure(e)
    }
  }
}
