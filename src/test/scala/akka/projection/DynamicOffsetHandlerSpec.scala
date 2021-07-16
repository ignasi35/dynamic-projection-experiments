package akka.projection

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 */
class DynamicOffsetHandlerSpec extends AnyWordSpec with Matchers {

  "DynamicOffsetHandler" should {

    val name = "ShoppingCart"

    "read regular offsets when restarting for the same slicing" in {
      // given some offsets on the offset store
      val dbOffsets: Seq[LastTrackedOffset] =
        Seq(
          LastTrackedOffset(name, "00", 23),
          LastTrackedOffset(name, "01", 35),
          LastTrackedOffset(name, "10", 42),
          LastTrackedOffset(name, "11", 11))

      val expected: Set[RequestedOffset] =
        Set(
          RequestedOffset("00", Some(23)),
          RequestedOffset("01", Some(35)),
          RequestedOffset("10", Some(42)),
          RequestedOffset("11", Some(11)))
      DynamicOffsetHandler.translate(dbOffsets, 4) should be(expected)
    }

    "reject slicing by non-power of 2 number of slices" in {
      assertThrows[IllegalArgumentException] {
        DynamicOffsetHandler.translate(Seq.empty, 3)
      }
    }

    "reject downscaling" in {
      val dbOffsets: Seq[LastTrackedOffset] =
        Seq(LastTrackedOffset(name, "0", 23), LastTrackedOffset(name, "1", 35))
      assertThrows[IllegalArgumentException] {
        DynamicOffsetHandler.translate(dbOffsets, 1)
      }
    }

    "reject database offsets if there's a mix of key sizes" in {
      val dbOffsets: Seq[LastTrackedOffset] =
        Seq(LastTrackedOffset(name, "0", 23), LastTrackedOffset(name, "10", 35))
      assertThrows[IllegalArgumentException] {
        DynamicOffsetHandler.translate(dbOffsets, 1)
      }
    }

    "produce empty offsets for missing DB rows (some offsets never got stored)" in {
      // given an incomplete set of offset (fewer offsets than
      // expected given a number of slices)
      val dbOffsets: Seq[LastTrackedOffset] =
        Seq(
          LastTrackedOffset(name, "10", 42),
          LastTrackedOffset(name, "11", 11))

      val expected: Set[RequestedOffset] =
        Set(
          RequestedOffset("00", None),
          RequestedOffset("01", None),
          RequestedOffset("10", Some(42)),
          RequestedOffset("11", Some(11)))
      DynamicOffsetHandler.translate(dbOffsets, 4) should be(expected)
    }

    "produce empty offsets for an empty DB " in {
      // given an incomplete set of offset (fewer offsets than
      // expected given a number of slices)
      val dbOffsets: Seq[LastTrackedOffset] = Seq.empty

      val expected: Set[RequestedOffset] =
        Set(
          RequestedOffset("00", None),
          RequestedOffset("01", None),
          RequestedOffset("10", None),
          RequestedOffset("11", None))
      DynamicOffsetHandler.translate(dbOffsets, 4) should be(expected)
    }

    "produce new slices when upscaling" in {
      val dbOffsets: Seq[LastTrackedOffset] =
        Seq(LastTrackedOffset(name, "0", 23), LastTrackedOffset(name, "1", 35))

      val expected: Set[RequestedOffset] =
        Set(
          RequestedOffset("00", Some(23)),
          RequestedOffset("01", Some(23)),
          RequestedOffset("10", Some(35)),
          RequestedOffset("11", Some(35)))
      DynamicOffsetHandler.translate(dbOffsets, 4) should be(expected)
    }

    "upscale a partial dataset" in {
      val dbOffsets: Seq[LastTrackedOffset] =
        Seq(LastTrackedOffset(name, "0", 23))

      val expected: Set[RequestedOffset] =
        Set(
          RequestedOffset("00", Some(23)),
          RequestedOffset("01", Some(23)),
          RequestedOffset("10", None),
          RequestedOffset("11", None))
      DynamicOffsetHandler.translate(dbOffsets, 4) should be(expected)
    }

  }

}

// Values read from the offset_store table
case class LastTrackedOffset(
    projectionName: String,
    projectionKey: String,
    currentOffset: Int)

// values the DynamicOffsetHandler computes to run the query on the journal
case class RequestedOffset(sliceId: String, readFromOffset: Option[Int])

object DynamicOffsetHandler {

  def translate(
      dbOffsets: Seq[LastTrackedOffset],
      numberOfSlices: Int): Set[RequestedOffset] = {
    require(
      isPowerOfTwo(numberOfSlices),
      "Number of slices must be a power of 2")
    require(
      dbOffsets.isEmpty ||
      dbOffsets.map(_.projectionKey).groupBy(_.length).size == 1,
      "The data on the offset table uses different length projection_key's. This usually means an upscale or downscale operation didn't complete. Please fix the data on the offset_store manually.")

    val mapOfOffsets: Map[String, LastTrackedOffset] =
      dbOffsets.map(dbo => (dbo.projectionKey -> dbo)).toMap

    // when scaling, the desired projectionKeySet and the legacyProjectionKeySet are different
    val desiredProjectionKeys: Seq[String] = ProjectionKeyGenerator.projectionKeys(numberOfSlices)
    val legacyProjectionKeys: Seq[String] = {
      if (dbOffsets.isEmpty) desiredProjectionKeys
      else ProjectionKeyGenerator.projectionKeys(1 << dbOffsets.map(_.projectionKey).head.length)
    }

    // scaleFactor indicates how many bits must be added on the projection keys
    // retrieved from the database in order to reach the desired number of slices.
    // 0 means there's no scaling
    // -1 means downscaling which is not supported
    val scaleFactor = desiredProjectionKeys.size/legacyProjectionKeys.size - 1
    require(scaleFactor>= 0, "Downscaling is mot supported.")

    def upscale(upscaleFactor: Int)(
        legacy: RequestedOffset): Seq[RequestedOffset] = {
      upscaleFactor match {
        case 0 => Seq(legacy)
        case n =>
          ProjectionKeyGenerator.projectionKeys(1 << n).map { suffix =>
            RequestedOffset(legacy.sliceId + suffix, legacy.readFromOffset)
          }
      }
    }

    def dbToRequest(
        projectionKey: String,
        dbOffset: Option[LastTrackedOffset]): Seq[RequestedOffset] =
      upscale(scaleFactor) {
        dbOffset match {
          case None =>
            RequestedOffset(projectionKey, None)
          case Some(dbo) =>
            RequestedOffset(dbo.projectionKey, Some(dbo.currentOffset))
        }
      }

    legacyProjectionKeys.flatMap { projectionKey =>
      dbToRequest(projectionKey, mapOfOffsets.get(projectionKey))
    }.toSet

  }

  private def isPowerOfTwo(i: Int): Boolean = (i & (i - 1)) == 0

}

class ProjectionKeyGeneratorSpec extends AnyWordSpec with Matchers {

  "ProjectionKeyGenerator" should {

    "generate projection keys" in {
      ProjectionKeyGenerator.projectionKeys(3) should be(Seq("00", "01", "10"))
      ProjectionKeyGenerator.projectionKeys(8) should be(
        Seq("000", "001", "010", "011", "100", "101", "110", "111"))
    }
  }
}

object ProjectionKeyGenerator {

  // Given a number of slices, produce all the binary keys in String representation required.
  def projectionKeys(numberOfSlices: Int): Seq[String] = {
    val unpadded =
      (0 until numberOfSlices).map(c => java.lang.Integer.toBinaryString(c))

    // left-pads the input to the desired length
    def pad(desiredLength: Int)(in: String): String = {
      (0 until (desiredLength - in.length)).map(_ => "0").mkString + in
    }
    val len = java.lang.Integer.toBinaryString(numberOfSlices - 1).length
    unpadded.map(pad(len))
  }

}
