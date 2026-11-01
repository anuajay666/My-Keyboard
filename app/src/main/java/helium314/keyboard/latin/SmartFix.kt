// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.content.Context
import android.provider.UserDictionary
import java.util.Locale

/**
 * Inkly's extra autocorrect layer, on top of the normal dictionary engine:
 *  - a table of very common typing mistakes and chat shortcuts that are always fixed,
 *  - a starter list of words that are special to Anu (banking terms, places, local words),
 *    so they are never "corrected" away and are suggested straight away.
 * Everything the user types often is also learned by the engine itself (history dictionary,
 * and after a few uses it is added to the personal dictionary).
 */
object SmartFix {
    private val typos: Map<String, String> = buildMap {
        fun p(vararg pairs: String) { for (s in pairs) { val (a, b) = s.split("=", limit = 2); put(a, b) } }
        // contractions without apostrophe
        p("dont=don't", "cant=can't", "wont=won't", "didnt=didn't", "doesnt=doesn't", "isnt=isn't", "wasnt=wasn't",
            "werent=weren't", "arent=aren't", "couldnt=couldn't", "shouldnt=shouldn't", "wouldnt=wouldn't",
            "havent=haven't", "hasnt=hasn't", "hadnt=hadn't", "youre=you're", "theyre=they're", "thats=that's",
            "whats=what's", "im=I'm", "ive=I've", "ur=your", "youve=you've", "youll=you'll", "theyll=they'll",
            "theyve=they've", "weve=we've", "wheres=where's", "hows=how's", "whos=who's", "heres=here's",
            "theres=there's", "shouldve=should've", "couldve=could've", "wouldve=would've", "mustnt=mustn't",
            "i=I", "dnt=don't", "cnt=can't")
        // typical typing slips
        p("teh=the", "hte=the", "thw=the", "tbe=the", "adn=and", "nad=and", "taht=that", "thta=that", "tath=that",
            "wiht=with", "whit=with", "wih=with", "wtih=with", "jsut=just", "juts=just", "yuo=you", "yoy=you",
            "yuor=your", "yoru=your", "thier=their", "tehir=their", "ther=there", "thne=then", "tehn=then",
            "thn=then", "waht=what", "whta=what", "wich=which", "whihc=which", "wihch=which", "becuase=because",
            "becasue=because", "beacuse=because", "bcoz=because", "becouse=because", "beause=because",
            "recieve=receive", "recieved=received", "reciept=receipt", "beleive=believe", "belive=believe",
            "freind=friend", "freinds=friends", "wierd=weird", "definately=definitely", "definitly=definitely",
            "definatly=definitely", "seperate=separate", "occured=occurred", "occurence=occurrence", "untill=until",
            "tommorow=tomorrow", "tomorow=tomorrow", "tommorrow=tomorrow", "tomorrw=tomorrow", "yesturday=yesterday",
            "yesterady=yesterday", "accomodate=accommodate", "adress=address", "addres=address", "begining=beginning",
            "calender=calendar", "comming=coming", "enviroment=environment", "existance=existence", "foriegn=foreign",
            "goverment=government", "govenment=government", "happend=happened", "hapenned=happened",
            "immediatly=immediately", "knowlege=knowledge", "libary=library", "neccessary=necessary",
            "necesary=necessary", "noticable=noticeable", "occassion=occasion", "persistant=persistent",
            "posession=possession", "prefered=preferred", "publically=publicly", "realy=really", "reallly=really",
            "refering=referring", "relevent=relevant", "responsability=responsibility", "sucessful=successful",
            "succesful=successful", "suprise=surprise", "surprize=surprise", "truely=truly", "wether=whether",
            "writting=writing", "writen=written", "alot=a lot", "eventhough=even though",
            "infact=in fact", "inspite=in spite", "aswell=as well", "atleast=at least", "thankyou=thank you",
            "thanx=thanks", "thnks=thanks", "thanks=thanks", "pls=please", "plz=please", "plese=please",
            "wil=will", "wiil=will", "wll=will", "woudl=would", "wuold=would", "shoudl=should",
            "coudl=could", "cuold=could", "somthing=something", "somethng=something", "anythng=anything", "everythng=everything", "evrything=everything",
            "nothin=nothing", "differnt=different", "diffrent=different", "importent=important", "inportant=important",
            "improtant=important", "meetin=meeting", "meating=meeting", "mesage=message",
            "mesages=messages", "recieving=receiving", "sendind=sending", "sedning=sending", "apponitment=appointment", "appointmnt=appointment", "apointment=appointment", "bussiness=business",
            "buisness=business", "busines=business", "compnay=company", "comapny=company", "custmer=customer",
            "costomer=customer", "custommer=customer", "acount=account", "accout=account", "accunt=account",
            "acconut=account", "banck=bank", "bnak=bank", "laon=loan", "paymnet=payment",
            "payement=payment", "pyament=payment", "ammount=amount", "amoutn=amount", "amout=amount",
            "intrest=interest", "interst=interest", "documnet=document", "docuemnt=document", "documet=document",
            "applicaiton=application", "aplication=application", "applcation=application", "managr=manager",
            "mangaer=manager", "brach=branch", "branh=branch", "reciept=receipt", "sancton=sanction",
            "sanciton=sanction", "sanctoin=sanction", "ofcourse=of course", "wnat=want", "ahve=have", "hvae=have",
            "hav=have", "hve=have", "ahd=had", "hda=had", "kno=know", "knwo=know", "konw=know", "dnt=don't",
            "mkae=make", "amke=make", "maek=make", "tkae=take", "taek=take", "ealier=earlier", "eariler=earlier",
            "peopel=people", "poeple=people", "pepole=people", "worng=wrong", "wrogn=wrong", "rihgt=right",
            "riht=right", "righ=right", "togehter=together", "togther=together", "agian=again", "agin=again",
            "anohter=another", "anotehr=another", "abotu=about", "aobut=about", "abuot=about", "afte=after",
            "aftr=after", "befor=before", "befroe=before", "firts=first", "frist=first", "secnod=second",
            "sicne=since", "sine=since", "smae=same", "saem=same", "tiem=time", "tmie=time", "yera=year",
            "ytear=year", "mroe=more", "moer=more", "thsi=this", "tihs=this", "htis=this", "thsoe=those",
            "thses=these", "thsee=these", "whn=when", "wehn=when", "whne=when", "wher=where", "wehre=where",
            "whre=where", "hwo=who", "hwy=why", "wyh=why", "hwat=what", "hwen=when", "howw=how", "nwo=now",
            "onw=own", "oen=one", "ony=only", "olny=only", "onyl=only", "reallly=really", "fine.=fine.",
            "gud=good", "gd=good", "gooood=good", "goood=good", "morning=morning", "mornin=morning",
            "evening=evening", "evenin=evening", "nite=night", "tonite=tonight", "tonght=tonight", "tonihgt=tonight",
            "thru=through", "tho=though", "throught=thought", "thougth=thought", "thoguht=thought", "lenght=length",
            "heigth=height", "wieght=weight", "wiegth=weight", "becomming=becoming", "geting=getting",
            "gettin=getting", "puting=putting", "runing=running", "stoping=stopping", "planing=planning",
            "swiming=swimming", "sitting=sitting", "writeing=writing", "comeing=coming", "haveing=having",
            "makeing=making", "takeing=taking", "usefull=useful", "carefull=careful", "beutiful=beautiful",
            "beautifull=beautiful", "wonderfull=wonderful", "sucess=success", "sucessfully=successfully",
            "adition=addition", "additon=addition", "necessery=necessary", "neccesary=necessary", "occurr=occur",
            "ocur=occur", "excelent=excellent", "excellant=excellent", "experiance=experience", "experence=experience",
            "grammer=grammar", "grammar=grammar", "independant=independent", "intresting=interesting",
            "intersting=interesting", "judgement=judgment", "maintainance=maintenance", "millenium=millennium",
            "mischievious=mischievous", "pastime=pastime", "perseverence=perseverance", "priviledge=privilege",
            "pronounciation=pronunciation", "refered=referred", "rythm=rhythm", "sieze=seize", "strenght=strength",
            "tendancy=tendency", "tyranny=tyranny", "vaccum=vacuum", "vehical=vehicle", "wensday=Wednesday",
            "wednsday=Wednesday", "thrusday=Thursday", "thursay=Thursday", "tuesay=Tuesday", "tusday=Tuesday",
            "mondy=Monday", "frday=Friday", "firday=Friday", "saterday=Saturday", "satruday=Saturday",
            "sundya=Sunday", "feburary=February", "febuary=February", "janurary=January", "septmber=September",
            "octber=October", "novmber=November", "decmber=December")
    }.filterKeys { it != "fine." }.filter { it.key != it.value }

    /** Returns the fixed word for [typed] (keeping capitals the way the user typed them), or null if it needs no fix. */
    @JvmStatic
    fun fix(typed: String): String? {
        if (typed.length < 1 || typed.length > 24) return null
        val low = typed.lowercase(Locale.ENGLISH)
        val target = typos[low] ?: return null
        // a lone "i" / "im" etc. is only a slip when typed in lower case; "I" itself is fine as typed
        if (typed == target) return null
        return when {
            low == "i" -> "I"
            typed.length > 1 && typed.all { it.isUpperCase() } && target.length > 1 -> target.uppercase(Locale.ENGLISH)
            typed[0].isUpperCase() -> target.replaceFirstChar { it.uppercase(Locale.ENGLISH) }
            else -> target
        }
    }

    // Starter words: never corrected away, suggested at once. (Editable later in Settings > Personal dictionary.)
    private val seed = listOf(
        "IOB", "DSCR", "TNW", "TOL", "CAM", "TEV", "KCC", "LENDperfect", "LENDperfect", "NPA", "CIBIL", "KYC", "NEFT", "RTGS", "IMPS",
        "UPI", "OTP", "IFSC", "MICR", "EMI", "ROI", "CGTMSE", "PMEGP", "MUDRA", "SARFAESI", "OCC", "DP", "TL", "CC",
        "Thenmalai", "Tirunelveli", "Tenkasi", "Thiruvananthapuram", "Trivandrum", "Nagercoil", "Madurai", "Chennai", "Kerala", "Tamil Nadu",
        "Indian Overseas Bank", "Anu", "Ajay", "Kannan", "Inkly", "SplitUp", "Gemma", "Malayalam", "Tamil",
        "vanakkam", "nandri", "namaskaram", "nanni", "enna", "illa", "sheri", "seri", "romba", "nalla", "machan", "macha",
        "ente", "entha", "enthu", "evide", "poyi", "vannu", "aanu", "alle", "ayyo", "chetta", "chechi", "mone", "mole", "ammachi", "appachan",
        "amma", "appa", "anna", "akka", "thambi", "thangachi", "paavam", "thanks", "okay", "ok",
    ).distinct()

    @JvmStatic
    fun seedOnce(ctx: Context) {
        val prefs = ctx.getSharedPreferences("inkly_smart", Context.MODE_PRIVATE)
        if (prefs.getInt("seed", 0) >= 1) return
        Thread {
            var ok = 0
            for (w in seed) {
                runCatching { UserDictionary.Words.addWord(ctx, w, 250, null, null as Locale?); ok++ }
            }
            // only mark as done if the phone accepted words; otherwise try again on the next start
            if (ok > seed.size / 2) prefs.edit().putInt("seed", 1).apply()
        }.start()
    }
}
