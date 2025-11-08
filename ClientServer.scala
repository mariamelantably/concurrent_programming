import ox.scl._

class ManWomanServer(BuffSize : Int){
    private val acquireRequestChan = new BuffChan[(Int, String, Chan[String])](BuffSize)
    private val shutdownChan = new SyncChan[Unit]
    def shutdown = shutdownChan!()

    def requestMan(me : String, returnChan : Chan[String]) : String = {
        acquireRequestChan!((1, me, returnChan))
        returnChan?()
    }
    
    def requestWoman(me: String, returnChan: Chan[String]) : String = {
        acquireRequestChan!((0, me, returnChan))
        returnChan?()
    }

    private def server = thread{
        val men = new scala.collection.mutable.Queue[(String, Chan[String])]
        val women = new scala.collection.mutable.Queue[(String, Chan[String])]
        serve(
            acquireRequestChan =?=> {x => 
                val i = x._1; val name =x._2; val replyChan = x._3; 
                if (i == 0){if (!women.isEmpty) {val (n, chan) = women.dequeue(); chan!(name); replyChan!(n)} else {men.enqueue((name, replyChan))}}
                else {{if (!men.isEmpty) {val (n, chan) = men.dequeue(); chan!(name); replyChan!(n)} else women.enqueue((name, replyChan))}}
            }
            | shutdownChan =?=> {_=> acquireRequestChan.endOfStream; shutdownChan.close}
        )
    }

    server.fork

}

//Here is the logic formula that must be satisfied. Let M be the set of all men and W be the set of all women
//Let R be the binary relation such that R(i,j) iff j was paired to i
//∀ m ∈ M, w ∈ W R(m, w) ⟺ R(w, m) ∧ R(m, w) ⇒ ∀m2 m != m2, ¬R(m2, w) ∧ ∀w2 w != w2, ¬R(m, w2)
 

object ClientServer{
    val n = 5
    trait LogEvent
    case class ManPair(man : String, woman : String) extends LogEvent
    case class WomanPair(woman : String, man: String) extends LogEvent
    val server = new ManWomanServer(5)
    val log = new Log[LogEvent](6)
    
    val logThis = new SyncChan[LogEvent]
    //prevents a memory race for the logs
    def toLog = thread{
        for (_ <- 0 until 2 * n) log.add(0, logThis?())
    }   

    def manSync(me: String) = thread{
        val replyChan = new SyncChan[String]
        val woman = server.requestWoman(me, replyChan)
        logThis!(ManPair(me, woman))
        println("Man: " + me + " Woman: " + woman)
    }

    def womanSync(me: String) = thread{
        val replyChan = new SyncChan[String]
        val man = server.requestMan(me, replyChan)
        logThis!(WomanPair(me, man))
        println("Woman: " + me + " Man: " + man)
    }

    //For testing we need to check the following properties:
    // 1. if WomanPair(i,j), then ManPair(j, i)  
    // 2. if ManPair(i, j), then WomanPair(j, i)
    // 3. if WomanPair(i, j), then there is no k such that WomanPair(k, j)
    // 4. if ManPair(i, j), then there is no k such that ManPair(k, j)

    def checkLog(events : Array[LogEvent]) : Boolean = {
        var error = false; var i = 0; val n = events.size
        while (i< n){
            println(i)
            events(i) match{
                case WomanPair(c, d) =>
                    var k = 0 
                    var found = false 
                    while (k < n){
                        events(k) match{
                            case WomanPair(a, b) => if (a == c && b != d) {return false} 
                            case ManPair(a, b) => if (b == c) {if (a == d) {found = true} else {return false}}
                        }
                        k += 1

                    }
                    if (!found) return false
                case ManPair(c,d) =>
                    var k = 0 
                    var found = false 
                    while (k < n){
                        events(k) match{
                            case ManPair(a, b) => if (a == c && b != d) {return false} 
                            case WomanPair(a, b) => if (b == c) {if (a == d) {found = true} else {return false}}
                        }
                        k += 1
                    }
                    if (!found) return false
            }
            i += 1
        }
    return true
}

    
    def main(args : Array[String]) = {
        log.writeToFileOnShutdown("man_woman_sync.txt")
        val p = ||(for (i <- 0 until n) yield womanSync(i.toString()))
        val q = ||(for (i <- 0 until n) yield manSync(i.toString()))
        run(p || q || toLog)
        println("Everything finished running - time to check for errors!")
        server.shutdown
        println("")
        println(checkLog(log.get))
        println("Everything done")
    }
}