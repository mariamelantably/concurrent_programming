import ox.scl._

class Adaptive(f: Double => Double, a: Double, b: Double, Epsilon: Double, nWorkers: Int){ 
    require(a <= b)
    val toController = new SyncChan[Option[(Double, Double, Double)]]
    val tasks = new SyncChan[(Double, Double)]
    val toCollector = new SyncChan[(Double, Double, Double, Double, Double)] //we send (res0, res1, res2, a, b)
    var sum = 0.0

    def distributor(in: ??[Option[(Double, Double, Double)]], out: !![(Double, Double)]) = thread{
        val q = new scala.collection.mutable.Queue[(Double, Double)]
        q.enqueue((a,b))
        var count_done = 0
        var count_to_do = 1
        repeat(count_done < count_to_do){
            println(count_done, count_to_do)
            alt(
                (!q.isEmpty) && out =!=> {val x = q.dequeue(); x}
                | in =?=> {x => count_done += 1; if (x.isDefined) {val x1 = x.get; count_to_do += 2; q.enqueue((x1._1, x1._2)); q.enqueue((x1._2, x1._3))};}          
            )
        }
        in.close; out.endOfStream
    }

    def worker = thread{
        repeat{
            val (i, j) = tasks?(); val mid = (i+j)/2.0
            val fa = f(i); val fb = f(j); val fmid = f(mid) 
            val lArea = (fa+fmid)*(mid-i)/2; val rArea = (fmid+fb)*(j-mid)/2; val area = (fa+fb)*(j-i)/2
            toCollector!(lArea, rArea, area, i, j)
        }
        toCollector.close;
    }

    def collector = thread{
        repeat{
            val (i, j, k, a, b) = toCollector?()
            println(i, j, k)
            println(a,b)
            if (Math.abs(i + j - k) < Epsilon){
                sum += k
                toController!(None)
            }
            else{
                val mid = (a+b)/2.0
                toController!(Some((a, mid, b)))
            }
        }
    }
    def apply(): Double = {
        val workers = || (for (i <- 0 until nWorkers) yield worker)
        run(workers || distributor(toController, tasks) || collector)
        return sum
    }

}

/* Ways to make the code more efficient */
// Store values for integrals/f(a) values in a hashmap or something similar to allow them to be reused
// Inside each worker, calculate the areas concurrently in a sub-thread

class ManWomanServer(BuffSize : Int){
    private val acquireRequestChan = new BuffChan[(Int, String, Chan[String])](BuffSize)
    private val shutdownChan = new SyncChan[Unit]

    trat 

    def shutdown = shutdownChan!()

    def server = thread{
        val men = new scala.collection.mutable.Queue[(String, Chan[String])]
        val women = new scala.collection.mutable.Queue[(String, Chan[String])]
        serve(
            acquireRequestChan =?=> {x => 
                val i = x._1; val name =x._2; val replyChan = x._3; 
                if (i == 0){if (!women.isEmpty) {val (n, chan) = women.dequeue(); chan!(name); replyChan!(n)} else {men.enqueue((name, replyChan))}}
                else {{if (!men.isEmpty) {val (n, chan) = men.dequeue(); chan!(name); replyChan!(n)} else women.enqueue((name, replyChan))}}
            }
            | shutdownChan =?=> {_=>acquireRequestChan.close}
        )
    }

    server.fork


    def manSync(me: String) : String = {
        val replyChan = new SyncChan[String]
        acquireRequestChan!(0, me, replyChan)
        val woman = replyChan?()
        woman
    }

    def womanSync(me: String) : String = {
        val replyChan = new SyncChan[String]
        acquireRequestChan!(1,me, replyChan)
        val man = replyChan?()
        man
    }
}


object ConcurrentProg_Sheet2_Elantably_Code{
    /* Question 1 */

    def multiplex[T](in0: ??[T], in1: ??[T], out0: !![T], out1: !![T]): ThreadGroup = thread{
        val mid = new SyncChan[(Int, T)]

        def tagger = thread{
            serve(
                in0 =?=> {x => mid!(0,x)}
                | in1 =?=> {x => mid!(1,x)}
            )
            in0.close; in1.close; mid.endOfStream
        }

        def detagger = thread{
            repeat{
            val (i, x) = mid?()
            if (i == 0) out0!x 
            else out1!x  
            }
            mid.close; out0.endOfStream; out1.endOfStream
        }
    }

    //The system terminates if any of the input or output ports close
    //The system will only progress if the input channels are in use; if they both block, then the system will not do anything useful
    //The system is fair, as the serve in the tagger allows us to alternate between both input channels.


    /* Question 2 */
    def ChangeMachine(inPound: ??[Unit], out5p: !![Unit], out10p: !![Unit], out20p: !![Unit]) = thread{
        var value = 0
        serve(
            (value == 0) && inPound =?=> {x => value = 100}
            | (value >= 5) && out5p =!=> {value -= 5}
            | (value >= 10) && out10p =!=> {value -= 10}
            | (value >= 20) && out20p =!=> {value -= 20}
        )
    }


    /* Question 3 */
    def buff[T](in : ??[T], out: !![T]) = thread{
        val q = new scala.collection.mutable.Queue[T]
        serve(
            (!q.isEmpty) && out =!=> {val x = q.dequeue(); x}
            | in =?=> {x => q.enqueue(x)}
        )
    }

    def f(a: Double): Double = return 3 * a * a

    def testServer(n: Int) = thread{
        val log = Log[String]
        val p = new ManWomanServer(5)
        var t = p.server
        for(i<- 0 until n) {t = t || thread{val x = p.manSync(i.toString()); println("Woman " + x.toString() + " is paired with Man " + i.toString())} || thread{val x = p.womanSync(i.toString()); println("Man " + x.toString() + " is paired with Woman " + i.toString())}}
        run(t)
    }
    def main(args : Array[String]) = {run(testServer(5)); println("Hello")}
} 