import akka.{Done, NotUsed}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import akka.NotUsed
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorSystem, Props, _}
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka_typed.CalculatorRepository.{getLatestOffsetAndResult, initDataBase}
import akka_typed.TypedCalculatorWriteSide.{Add, Added, Command, Divide, Divided, Multiplied, Multiply}

import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.{Failure, Success}
import akka.NotUsed
import akka.actor.typed._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import akka.stream.ClosedShape
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source}
import akka_typed.CalculatorRepository._
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps


case class Action(value: Int, name: String)



object akka_typed {

  trait CborSerializable

  val persId = PersistenceId.ofUniqueId("001")

  object TypedCalculatorWriteSide {
    sealed trait Command
    case class Add(amount: Int)      extends Command
    case class Multiply(amount: Int) extends Command
    case class Divide(amount: Int)   extends Command

    sealed trait Event
    case class Added(id: Int, amount: Int)      extends Event
    case class Multiplied(id: Int, amount: Int) extends Event
    case class Divided(id: Int, amount: Int)    extends Event
    case class CalculationCompleted(id: Int, amount: Int)    extends Event

    final case class State(value: Int) extends CborSerializable {
      def add(amount: Int): State      = copy(value = value + amount)
      def multiply(amount: Int): State = copy(value = value * amount)
      def divide(amount: Int): State   = copy(value = value / amount)
    }

    object State {
      val empty = State(0)
    }

    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      EventSourcedBehavior[Command, Event, State](
        persistenceId = persId,
        State.empty,
        (state, command) => handleCommand("001", state, command, ctx),
        (state, event) => handleEvent(state, event, ctx)
      )
        //          .snapshotWhen{
        //            case (state, CalculationCompleted(_, _), seqNumber) if seqNumber % 10 == 0 => true
        //            case (state, event, seqNumber) => false
        //          }
        .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 2)
          .withDeleteEventsOnSnapshot)
    }

    def handleCommand(
                       persistenceId: String,
                       state: State,
                       command: Command,
                       ctx: ActorContext[Command]
                     ): Effect[Event, State] =
      command match {
        case Add(amount) =>
          ctx.log.info(s"Receive adding for number: $amount and state is ${state.value}")
          val added = Added(persistenceId.toInt, amount)
          Effect
            .persist(added)
            .thenRun { x =>
              ctx.log.info(s"The state result is ${x.value}")
            }
        case Multiply(amount) =>
          ctx.log.info(s"Receive multiplying for number: $amount and state is ${state.value}")
          Effect
            .persist(Multiplied(persistenceId.toInt, amount))
            .thenRun { newState =>
              ctx.log.info(s"The state result is ${newState.value}")
            }
        case Divide(amount) =>
          ctx.log.info(s"Receive dividing for number: $amount and state is ${state.value}")
          Effect
            .persist(Divided(persistenceId.toInt, amount))
            .thenRun { x =>
              ctx.log.info(s"The state result is ${x.value}")
            }
      }

    def handleEvent(state: State, event: Event, ctx: ActorContext[Command]): State =
      event match {
        case Added(_, amount) =>
          ctx.log.info(s"Handing event amount is $amount and state is ${state.value}")
          state.add(amount)
        case Multiplied(_, amount) =>
          ctx.log.info(s"Handing event amount is $amount and state is ${state.value}")
          state.multiply(amount)
        case Divided(_, amount) =>
          ctx.log.info(s"Handing event amount is $amount and state is ${state.value}")
          state.divide(amount)
      }
  }

  case class TypedCalculatorReadSide(system: ActorSystem[NotUsed]) {
    initDataBase
    implicit val materializer            = system.classicSystem
    implicit val session: SlickSession = SlickSession.forConfig("slick-postgres")
    materializer.registerOnTermination(() => session.close())
    import session.profile.api._

    var (offset, latestCalculatedResult) = getLatestOffsetAndResult
    val startOffset: Int                 = if (offset == 1) 1 else offset + 1

    val readJournal: CassandraReadJournal =
      PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

    val source: Source[EventEnvelope, NotUsed] = readJournal
      .eventsByPersistenceId("001", startOffset, Long.MaxValue)

    val flow: Flow[EventEnvelope, EventEnvelope, NotUsed] = Flow[EventEnvelope].map{
      event =>
        event.event match {
          case Added(_, amount) =>
            latestCalculatedResult += amount
            println(s"! Log from Added: $latestCalculatedResult")
          case Multiplied(_, amount) =>
            latestCalculatedResult *= amount
            println(s"! Log from Multiplied: $latestCalculatedResult")
          case Divided(_, amount) =>
            latestCalculatedResult /= amount
            println(s"! Log from Divided: $latestCalculatedResult")
        }
        event
    }

    val sink: Sink[EventEnvelope, Future[Done]] = Slick.sink {
      eventEnvelope =>
        sqlu"update public.result set calculated_value = ${latestCalculatedResult}, write_side_offset = ${eventEnvelope.sequenceNr} where id = 1"
    }

    val graph: RunnableGraph[NotUsed] = source
      .via(flow).async
      .to(sink).async

  }

  object CalculatorRepository {
    import scalikejdbc._

    def initDataBase: Unit = {
      Class.forName("org.postgresql.Driver")
      val poolSettings = ConnectionPoolSettings(initialSize = 10, maxSize = 100)

      ConnectionPool.singleton("jdbc:postgresql://localhost:5432/demo", "docker", "docker", poolSettings)
    }

    def getLatestOffsetAndResult: (Int, Double) = {
      val entities =
        DB readOnly { session =>
          session.list("select * from public.result where id = 1;") {
            row => (row.int("write_side_offset"), row.double("calculated_value")) }
        }
      entities.head
    }

  }


  def apply(): Behavior[NotUsed] =
    Behaviors.setup { ctx =>
      val writeActorRef = ctx.spawn(TypedCalculatorWriteSide(), "Calculato", Props.empty)

      writeActorRef ! Add(10)
      //      writeActorRef ! Multiply(2)
      //      writeActorRef ! Divide(5)

      (1 to 80).foreach{ x =>
        writeActorRef ! Add(10)
      }



      // 0 + 10 = 10
      // 10 * 2 = 20
      // 20 / 5 = 4

      Behaviors.same
    }


  def main(args: Array[String]): Unit = {
    val value = akka_typed()
    implicit val system: ActorSystem[NotUsed] = ActorSystem(value, "akka_typed")

    TypedCalculatorReadSide(system).graph.run()

    implicit val executionContext = system.executionContext
  }


}
