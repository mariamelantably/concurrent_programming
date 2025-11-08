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



object ConcurrentProg_Sheet2_Elantably_Code{
    def f(a: Double): Double = return 3 * a * a
    def main(args : Array[String]) = {}
} 
