package app.plait.data.quotes

data class Quote(val text: String, val author: String)

object Quotes {
    val all: List<Quote> = listOf(
        Quote("Efficiency is doing things right; effectiveness is doing the right things.", "Peter Drucker"),
        Quote("There is nothing so useless as doing efficiently that which should not be done at all.", "Peter Drucker"),
        Quote("Management is doing things right; leadership is doing the right things.", "Peter Drucker"),
        Quote("Plans are only good intentions unless they immediately degenerate into hard work.", "Peter Drucker"),
        Quote("The main thing is to keep the main thing the main thing.", "Stephen Covey"),
        Quote("The key is not to prioritize what's on your schedule, but to schedule your priorities.", "Stephen Covey"),
        Quote("Begin with the end in mind.", "Stephen Covey"),
        Quote("Most of us spend too much time on what is urgent and not enough on what is important.", "Stephen Covey"),
        Quote("Focus is about saying no.", "Steve Jobs"),
        Quote("Deciding what not to do is as important as deciding what to do.", "Steve Jobs"),
        Quote("Simple can be harder than complex.", "Steve Jobs"),
        Quote("Quality is more important than quantity. One home run is much better than two doubles.", "Steve Jobs"),
        Quote("Innovation distinguishes between a leader and a follower.", "Steve Jobs"),
        Quote("The difference between successful people and really successful people is that they say no to almost everything.", "Warren Buffett"),
        Quote("Someone is sitting in the shade today because someone planted a tree a long time ago.", "Warren Buffett"),
        Quote("Measuring programming progress by lines of code is like measuring aircraft building progress by weight.", "Bill Gates"),
        Quote("If you're not stubborn, you'll give up on experiments too soon.", "Jeff Bezos"),
        Quote("Work hard, have fun, make history.", "Jeff Bezos"),
        Quote("Invention requires a long-term willingness to be misunderstood.", "Jeff Bezos"),
        Quote("Coming together is a beginning; keeping together is progress; working together is success.", "Henry Ford"),
        Quote("Nothing is particularly hard if you divide it into small jobs.", "Henry Ford"),
        Quote("Genius is one percent inspiration and ninety-nine percent perspiration.", "Thomas Edison"),
        Quote("Opportunity is missed by most people because it is dressed in overalls and looks like work.", "Thomas Edison"),
        Quote("There is no substitute for hard work.", "Thomas Edison"),
        Quote("I have not failed. I've just found 10,000 ways that won't work.", "Thomas Edison"),
        Quote("Only the paranoid survive.", "Andy Grove"),
        Quote("Success breeds complacency.", "Andy Grove"),
        Quote("Your mind is for having ideas, not holding them.", "David Allen"),
        Quote("You can do anything, but not everything.", "David Allen"),
        Quote("Clarity about what matters provides clarity about what does not.", "Cal Newport"),
        Quote("Success is nothing more than a few simple disciplines, practiced every day.", "Jim Rohn"),
        Quote("Either you run the day or the day runs you.", "Jim Rohn"),
        Quote("Discipline is the bridge between goals and accomplishment.", "Jim Rohn"),
        Quote("Don't wish it were easier. Wish you were better.", "Jim Rohn"),
        Quote("You don't have to be great to start, but you have to start to be great.", "Zig Ziglar"),
        Quote("Lack of direction, not lack of time, is the problem. We all have twenty-four hour days.", "Zig Ziglar"),
        Quote("Don't be a know-it-all; be a learn-it-all.", "Satya Nadella"),
        Quote("Pain plus reflection equals progress.", "Ray Dalio"),
        Quote("If you're not failing, you're not innovating enough.", "Elon Musk"),
        Quote("Business opportunities are like buses, there's always another one coming.", "Richard Branson"),
        Quote("If you spend too much time thinking about a thing, you'll never get it done.", "Bruce Lee"),
        Quote("I fear not the man who has practiced 10,000 kicks once, but the man who has practiced one kick 10,000 times.", "Bruce Lee"),
        Quote("Absorb what is useful, discard what is not.", "Bruce Lee"),
        Quote("I've failed over and over and over again in my life. And that is why I succeed.", "Michael Jordan"),
        Quote("Some people want it to happen, some wish it would happen, others make it happen.", "Michael Jordan"),
        Quote("Talent wins games, but teamwork and intelligence win championships.", "Michael Jordan"),
        Quote("The harder you work, the harder it is to surrender.", "Vince Lombardi"),
        Quote("The only place success comes before work is in the dictionary.", "Vince Lombardi"),
        Quote("Perfection is not attainable, but if we chase perfection we can catch excellence.", "Vince Lombardi"),
        Quote("Be quick, but don't hurry.", "John Wooden"),
        Quote("You miss 100% of the shots you don't take.", "Wayne Gretzky"),
        Quote("The way to get started is to quit talking and begin doing.", "Walt Disney"),
        Quote("Live in the future, then build what's missing.", "Paul Graham"),
        Quote("If you are not embarrassed by the first version of your product, you've launched too late.", "Reid Hoffman"),
        Quote("Every minute you spend in planning saves ten minutes in execution.", "Brian Tracy"),
        Quote("Successful people are simply those with successful habits.", "Brian Tracy"),
        Quote("Being busy is a form of laziness.", "Tim Ferriss"),
        Quote("If you don't prioritize your life, someone else will.", "Greg McKeown"),
        Quote("Work expands so as to fill the time available for its completion.", "C. Northcote Parkinson"),
        Quote("Nothing in life is to be feared, it is only to be understood.", "Marie Curie"),
        Quote("Life is like riding a bicycle. To keep your balance, you must keep moving.", "Albert Einstein"),
        Quote("It's not that I'm so smart, it's just that I stay with problems longer.", "Albert Einstein"),
        Quote("Strive not to be a success, but rather to be of value.", "Albert Einstein"),
        Quote("In the middle of difficulty lies opportunity.", "Albert Einstein"),
        Quote("The way to get good ideas is to get lots of ideas, and throw the bad ones away.", "Linus Pauling"),
        Quote("Amateurs sit and wait for inspiration, the rest of us just get up and go to work.", "Stephen King"),
        Quote("You can't use up creativity. The more you use, the more you have.", "Maya Angelou"),
    )

    /**
     * Same slot on consecutive days steps by a stride coprime with the list size, so 7 days never
     * repeat; slots sit a third of the list apart.
     */
    fun pick(dayEpoch: Long, slot: Int): Quote {
        val n = all.size
        return all[Math.floorMod(dayEpoch * STRIDE + slot * (n / 3L), n.toLong()).toInt()]
    }

    private val STRIDE: Long = (all.size / 3L..all.size).first { gcd(it, all.size.toLong()) == 1L }
    private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
}
