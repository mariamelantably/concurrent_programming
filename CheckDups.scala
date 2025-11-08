import ox.scl._

class CheckForDuplicates(a : Array[Int], b : Array[Int], nTasks: Int, nWorkers : Int){
    require(nTasks%a.size == 0) 
    //we send workers (i,j) which tells them we want them to work on array a[i..j)
    private val toWorkers = new SyncChan[(Int, Int)] 
    
    //the worker sends back the number of elements in a[i..j) such that a(k) is in b
    private val toController = new SyncChan[Int] 

    private val bMap = scala.collection.mutable.HashMap.empty[Int, Boolean]
    for (k <- b) bMap.update(k, true)
    

    private def worker = thread("worker"){
        repeat{
            val (i, j) = toWorkers?()
            //find the count of elements in a[i..j) appearing in b
            var x = 0
            for (k <- i until j){
                if (bMap.contains(a(k))) x += 1
            }
            toController!(x) //send the count
        }
    }

    private def distributor = thread("distributor"){
        //we divide into nTasks taks
        var left = 0
        val taskRange = a.size/nTasks
        for (i <- 0 until nTasks){
            val right = left + taskRange
            toWorkers!(left, right)
            left = right
        }
        toWorkers.endOfStream
    }

    private def collector = thread("collector"){
        var s = 0
        for (a <- 0 until nTasks) {
            val t = toController?() //collect the task
            s += t //add to the sum
        }
    }

    private def system = {
        val workers = || (for (i <- 0 until nWorkers) yield worker)
        workers || distributor || collector
    }

    def apply = {run(system)}
}

