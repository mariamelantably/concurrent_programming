import ox.scl._

class Variant1(N: Int){
    val log = new Log[String](N)
    def eat = Thread.sleep(500)
    def think = Thread.sleep(scala.util.Random.nextInt(900))
    def pause = Thread.sleep(500)

    type Command = Boolean
    val Pick = true; val Drop = false

    def philLeft(me: Int, left: !![Command], right: !![Command]) = thread("Phil" + me){
        repeat{
            think
            log.add(me, me + " sits"); pause
            left!Pick; log.add(me, me+ " picks up left fork"); pause
            right!Pick; log.add(me, me+" picks up right fork"); pause
            log.add(me, me+" eats"); eat
            left!Drop; pause; right!Drop; pause
            log.add(me, me+" leaves")
            if (me == 0) print(".")
        }
    }

    def philRight(me: Int, left: !![Command], right: !![Command]) = thread("Phil" + me){
        repeat{
            think
            log.add(me, me + " sits"); pause
            right!Pick; log.add(me, me+" picks up right fork"); pause
            left!Pick; log.add(me, me+ " picks up left fork"); pause
            log.add(me, me+" eats"); eat
            left!Drop; pause; right!Drop; pause
            log.add(me, me+" leaves")
            if (me == 0) print(".")
        }
    }

    /* A single fork. */
    def fork(me: Int, left: ??[Command], right: ??[Command]) = thread("Fork"+me){
        serve(
            left =?=> {
                x => assert(x == Pick); val y = left?(); assert(y == Drop)
            }
            |
            right =?=> {
                x => assert(x == Pick); val y = right?(); assert(y == Drop)
            }
        )
    }

    def system = {
        val philToLeftFork, philToRightFork = Array.fill(N)(new SyncChan[Command])
        val allPhils = || (
            for (i <- 1 until N) yield philLeft(i, philToLeftFork(i), philToRightFork(i))
        )
        val allForks = || (
            for (i <- 0 until N) yield fork(i, philToRightFork((i+1)%N), philToLeftFork(i))
        )
        allPhils || philRight(0, philToLeftFork(0), philToRightFork(0)) || allForks
    }   

}

class Variant2(N: Int){
    val log = new Log[String](N)
    def eat = Thread.sleep(500)
    def think = Thread.sleep(scala.util.Random.nextInt(900))
    def pause = Thread.sleep(500)
    val askToEat = new SyncChan[Int]
    val doneEating = new SyncChan[Int]
    var currentlyEating = 0 

    type Command = Boolean
    val Pick = true; val Drop = false

    def butler(toEat : ??[Int], doneEat: ??[Int]) = thread("Butler"){
        serve(
            (currentlyEating < N - 1) && toEat =?=> {_ => currentlyEating += 1}
            | doneEat =?=> {_ => currentlyEating -= 1}
        )
    }

    def philLeft(me: Int, left: !![Command], right: !![Command]) = thread("Phil" + me){
        repeat{
            think
            askToEat!(me)
            log.add(me, me + " sits"); pause
            left!Pick; log.add(me, me+ " picks up left fork"); pause
            right!Pick; log.add(me, me+" picks up right fork"); pause
            log.add(me, me+" eats"); eat
            left!Drop; pause; right!Drop; pause
            doneEating!(me)
            log.add(me, me+" leaves")
            if (me == 0) print(".")
        }
    }

    /* A single fork. */
    def fork(me: Int, left: ??[Command], right: ??[Command]) = thread("Fork"+me){
        serve(
            left =?=> {
                x => assert(x == Pick); val y = left?(); assert(y == Drop)
            }
            |
            right =?=> {
                x => assert(x == Pick); val y = right?(); assert(y == Drop)
            }
        )
    }

    def system = {
        val philToLeftFork, philToRightFork = Array.fill(N)(new SyncChan[Command])
        val allPhils = || (
            for (i <- 0 until N) yield philLeft(i, philToLeftFork(i), philToRightFork(i))
        )
        val allForks = || (
            for (i <- 0 until N) yield fork(i, philToRightFork((i+1)%N), philToLeftFork(i))
        )
        allPhils || allForks || butler(askToEat, doneEating)
    }   

}

class Variant3(N: Int){
    val log = new Log[String](N)
    def eat = Thread.sleep(500)
    def think = Thread.sleep(scala.util.Random.nextInt(900))
    def pause = Thread.sleep(500)

    type Command = Boolean
    val Pick = true; val Drop = false

    def philLeft(me: Int, left: !![Command], right: !![Command]) = thread("Phil" + me){
        repeat{
            think
            log.add(me, me + " sits"); pause
            left!Pick; log.add(me, me+ " picks up left fork"); pause
            val sentFlag = right.sendWithin(scala.util.Random.nextInt(100) + 200)(Pick); 
            //We set a delay from 200-300 seconds, randomly to prevent blocking if they all drop at once
            if (sentFlag){ //the right fork was picked up 
                log.add(me, me+" picks up right fork");
                log.add(me, me+" eats"); eat
                left!Drop; pause; right!Drop; pause
            }
            else{
                log.add(me, me + " does not pick up right fork due to time out")
                left!Drop; pause
            }
            log.add(me, me+" leaves")
            if (me == 0) print(".")
        }
    }

    /* A single fork. */
    def fork(me: Int, left: ??[Command], right: ??[Command]) = thread("Fork"+me){
        serve(
            left =?=> {
                x => assert(x == Pick); val y = left?(); assert(y == Drop)
            }
            |
            right =?=> {
                x => assert(x == Pick); val y = right?(); assert(y == Drop)
            }
        )
    }

    def system = {
        val philToLeftFork, philToRightFork = Array.fill(N)(new SyncChan[Command])
        val allPhils = || (
            for (i <- 0 until N) yield philLeft(i, philToLeftFork(i), philToRightFork(i))
        )
        val allForks = || (
            for (i <- 0 until N) yield fork(i, philToRightFork((i+1)%N), philToLeftFork(i))
        )
        allPhils || allForks 
    }   

}


object DinPhils{
    def testVariant1(N : Int) = {
        val p = new Variant1(N)
        p.log.writeToFileOnShutdown("variant1.txt")
        run(p.system)
    }

    def testVariant2(N : Int) = {
        val p = new Variant2(N)
        p.log.writeToFileOnShutdown("variant2.txt")
        run(p.system)
    }

    def testVariant3(N : Int) = {
        val p = new Variant3(N)
        p.log.writeToFileOnShutdown("variant3.txt")
        run(p.system)
    }


    def main(args: Array[String]) =  testVariant3(6)
}