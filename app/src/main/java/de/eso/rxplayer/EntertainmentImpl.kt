package de.eso.rxplayer

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import io.reactivex.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit

class EntertainmentImpl(private val scheduler: Scheduler) : Entertainment {
    override val audio: Audio = AudioImpl(scheduler)
    override val usb: Player = PlayerImpl(scheduler, audio)
    override val cd: Player = PlayerImpl(scheduler, audio)
    override val fm: Radio = RadioImpl(scheduler, audio)
    override val speaker: Speaker = SpeakerImpl(audio, usb, cd, fm)
}

class SpeakerImpl(audio: Audio, usb: Player, cd: Player, fm: Radio) : Speaker {
    override fun observe(): Observable<SpeakerState> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class RadioImpl(private val scheduler: Scheduler, audio: Audio) : Radio {
    override fun list(): Observable<List<Station>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun nowPlaying(): Observable<Int> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun radioText(): Observable<Track> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun select(index: Int): Completable {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

class PlayerImpl(private val scheduler: Scheduler, audio: Audio) : Player {
    override fun nowPlaying(): Observable<Int> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun play(): Completable {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun pause(): Completable {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun select(index: Int): Completable {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun list(): Observable<List<Track>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

class AudioImpl(private val scheduler: Scheduler) : Audio {
    private val usb: BehaviorSubject<Audio.AudioState> = BehaviorSubject.createDefault(Audio.AudioState.STOPPED)
    private val cd: BehaviorSubject<Audio.AudioState> = BehaviorSubject.createDefault(Audio.AudioState.STOPPED)
    private val radio: BehaviorSubject<Audio.AudioState> = BehaviorSubject.createDefault(Audio.AudioState.STOPPED)

    private var pending: Disposable = Disposables.empty()

    override fun observe(connection: Audio.Connection): Observable<Audio.AudioState> {
        return subjectFor(connection).hide()
    }

    private fun subjectFor(connection: Audio.Connection): BehaviorSubject<Audio.AudioState> {
        return when (connection) {
            Audio.Connection.USB -> usb
            Audio.Connection.CD -> cd
            Audio.Connection.RADIO -> radio
        }
    }

    override fun start(connection: Audio.Connection) {
        scheduler.scheduleDirect {
            val subject = subjectFor(connection)
            when {
                subject.value != Audio.AudioState.STOPPED -> {
                    subject.onError(IllegalStateException("[AudioImpl.start] Can't start $connection because it is ${subject.value}"))
                }
                listOf(usb, cd, radio).map { it.value }.all { it == Audio.AudioState.STOPPED } -> {
                    subject.onNext(Audio.AudioState.STARTING)

                    pending.dispose()
                    pending = Single.timer(1, TimeUnit.SECONDS, scheduler)
                            .subscribe { _ -> subject.onNext(Audio.AudioState.STARTED) }
                }
                else -> {
                    subject.onError(IllegalStateException("[AudioImpl.start] Can't start $connection because there are connections still running"))
                }
            }
        }
    }

    override fun stop(connection: Audio.Connection) {
        scheduler.scheduleDirect {
            val subject = subjectFor(connection)
            if (subject.value == Audio.AudioState.STARTING || subject.value == Audio.AudioState.STARTED) {
                subject.onNext(Audio.AudioState.STOPPING)

                pending.dispose()
                pending = Single.timer(1, TimeUnit.SECONDS, scheduler)
                        .subscribe { _ -> subject.onNext(Audio.AudioState.STOPPED) }
            } else {
                subject.onError(IllegalStateException("[AudioImpl.start] Can't stop $connection because it is ${subject.value}"))
            }
        }
    }

    override fun fageId(connection: Audio.Connection): Completable {
        return Completable.defer {
            val subject = subjectFor(connection)
            return@defer when {
                subject.value == Audio.AudioState.STARTED -> Completable.timer(1, TimeUnit.MILLISECONDS, scheduler)
                else -> Completable.error(IllegalStateException("[AudioImpl.start] Can't fageId $connection because it is ${subject.value}"))
            }
        }.subscribeOn(scheduler)
    }

    override fun fadeOut(connection: Audio.Connection): Completable {
        return Completable.defer {
            val subject = subjectFor(connection)
            return@defer when {
                subject.value == Audio.AudioState.STARTED -> Completable.timer(1, TimeUnit.MILLISECONDS, scheduler)
                else -> Completable.error(IllegalStateException("[AudioImpl.start] Can't fadeOut $connection because it is ${subject.value}"))
            }
        }.subscribeOn(scheduler)
    }
}

data class Optional<T>(val of: T?) {
    fun isPresent(): Boolean = of != null
    fun get(): T = of!!
    fun or(defaultValue: T): T = of ?: defaultValue

    companion object {
        @JvmStatic
        fun <T> absent(): Optional<T> = Optional(null)

        @JvmStatic
        fun <T> fromNullable(value: T?): Optional<T> = Optional(value)

        @JvmStatic
        fun <T> of(value: T): Optional<T> = Optional(value)
    }
}