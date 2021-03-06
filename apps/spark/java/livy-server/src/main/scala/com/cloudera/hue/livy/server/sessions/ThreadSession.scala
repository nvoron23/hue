package com.cloudera.hue.livy.server.sessions

import java.net.URL

import com.cloudera.hue.livy.msgs.ExecuteRequest
import com.cloudera.hue.livy.repl
import com.cloudera.hue.livy.repl.python.PythonSession
import com.cloudera.hue.livy.repl.scala.SparkSession
import com.cloudera.hue.livy.server.Statement
import com.cloudera.hue.livy.server.sessions.Session._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

object ThreadSession {
  val LIVY_HOME = System.getenv("LIVY_HOME")
  val LIVY_REPL = LIVY_HOME + "/bin/livy-repl"

  def create(id: String, kind: Session.Kind): Session = {
    val session = kind match {
      case Session.Spark() =>
        SparkSession.create()
      case Session.PySpark() =>
        PythonSession.createPySpark()
    }
    new ThreadSession(id, kind, session)
  }
}

private class ThreadSession(val id: String,
                            val kind: Session.Kind,
                            session: com.cloudera.hue.livy.repl.Session) extends Session {

  protected implicit def executor: ExecutionContextExecutor = ExecutionContext.global

  private var executedStatements = 0
  private var statements_ = new ArrayBuffer[Statement]

  override def proxyUser: Option[String] = None

  override def lastActivity: Long = 0

  override def state: State = {
    session.state match {
      case repl.Session.NotStarted() => NotStarted()
      case repl.Session.Starting() => Starting()
      case repl.Session.Idle() => Idle()
      case repl.Session.Busy() => Busy()
      case repl.Session.ShuttingDown() => Dead()
      case repl.Session.ShutDown() => Dead()
      case repl.Session.Error() => Error()
    }
  }

  override def url: Option[URL] = None

  override def url_=(url: URL): Unit = {}

  override def executeStatement(content: ExecuteRequest): Statement = {
    val statement = new Statement(executedStatements, content, session.execute(content.code))

    executedStatements += 1
    statements_ += statement

    statement
  }

  override def statement(statementId: Int): Option[Statement] = statements_.lift(statementId)

  override def statements(): Seq[Statement] = statements_

  override def statements(fromIndex: Integer, toIndex: Integer): Seq[Statement] = statements_.slice(fromIndex, toIndex).toSeq

  override def interrupt(): Future[Unit] = {
    stop()
  }

  override def stop(): Future[Unit] = Future {
    session.close()
  }
}
