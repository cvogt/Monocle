package monocle.syntax

import monocle.{SimpleLens, Getter, Optional, Lens}
import scalaz.{Functor, State}

object lens extends LensSyntax

private[syntax] trait LensSyntax {
  implicit def toLensOps[S, T, A, B](lens:  Lens[S, T, A, B]): LensOps[S, T, A, B] = new LensOps(lens)
  implicit def toSimpleLensOps[S, A](lens:  SimpleLens[S, A]): SimpleLensOps[S, A] = new SimpleLensOps(lens)

  implicit def toApplyLensOps[S](value: S): ApplyLensOps[S] = new ApplyLensOps(value)
}

private[syntax] final class LensOps[S, T, A, B](self: Lens[S, T, A, B]) {
  def |->[C, D](other: Lens[A, B, C, D]): Lens[S, T, C, D] = self composeLens other
}

private[syntax] final class SimpleLensOps[S, A](self: SimpleLens[S, A]) {
  
  def updateState(f: A => A): State[S, Unit] =
    self.toState.leftMap(s =>
      self.modify(s, f)
    ).map(_ => ())

  def :=(value: A): State[S, Unit] = updateState(_ => value)
  
  def +=(value: A)(implicit ev: Numeric[A]): State[S, Unit] =
    updateState(a => ev.plus(a, value))

  def -=(value: A)(implicit ev: Numeric[A]): State[S, Unit] =
    updateState(a => ev.minus(a, value))

  def *=(value: A)(implicit ev: Numeric[A]): State[S, Unit] =
    updateState(a => ev.times(a, value))

  def zoom[B](subState: State[A, B]): State[S, B] =
    for {
      _ <- self.toState
      b <- State[S, B] { s =>
        val (a, b) = subState.apply(self.get(s))
        (self.set(s, a), b)
      }
    } yield b

}

private[syntax] trait ApplyLens[S, T, A, B] extends ApplyOptional[S, T, A, B] with ApplyGetter[S, A] { self =>
  def _lens: Lens[S, T, A, B]

  def _optional: Optional[S, T, A, B] = _lens
  def _getter: Getter[S, A] = _lens

  def lift[F[_]: Functor](f: A => F[B]): F[T] = _lens.lift[F](from, f)

  def composeLens[C, D](other: Lens[A, B, C, D]): ApplyLens[S, T, C, D] = new ApplyLens[S, T, C, D] {
    val from: S = self.from
    val _lens: Lens[S, T, C, D] = self._lens composeLens other
  }

  /** Alias to composeLens */
  def |->[C, D](other: Lens[A, B, C, D]): ApplyLens[S, T, C, D] = composeLens(other)
}

private[syntax] final class ApplyLensOps[S](value: S) {
  def applyLens[T, A, B](lens: Lens[S, T, A, B]): ApplyLens[S, T, A, B] = new ApplyLens[S, T, A, B] {
    val from: S = value
    def _lens: Lens[S, T, A, B] = lens
  }

  /** Alias to ApplyLens */
  def |->[T, A, B](lens: Lens[S, T, A, B]): ApplyLens[S, T, A, B] = applyLens(lens)
}