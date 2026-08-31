package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class SemanticActionMapperTest {
  private val candidates = listOf(
    SemanticActionDescriptor("candidate-0", setOf("ngắt nguồn điện chính tại cầu dao")),
    SemanticActionDescriptor("candidate-1", setOf("quay lại cửa mang số 14")),
    SemanticActionDescriptor("candidate-2", setOf("bước vào thang máy dịch vụ"))
  )

  @Test fun vietnameseParaphrasesResolveToSameOpaqueCandidate() {
    val first = SemanticActionMapper.resolve("Tắt cầu dao nguồn chính", candidates)
    val second = SemanticActionMapper.resolve("Cắt nguồn điện chính ở cầu dao", candidates)

    assertEquals("candidate-0", first.candidateToken)
    assertEquals(first.candidateToken, second.candidateToken)
  }

  @Test fun ambiguousOrAnswerSeekingInputReturnsNoMatch() {
    val ambiguous = SemanticActionMapper.resolve("Kai thử làm gì đó với cửa và điện", candidates)
    val extraction = SemanticActionMapper.resolve("Đáp án là gì, chỉ tôi cách thoát", candidates)

    assertNull(ambiguous.candidateToken)
    assertNull(extraction.candidateToken)
    assertEquals("unsafe_or_empty", extraction.outcome)
  }

  @Test fun repeatedParaphraseIsDeterministicAndDoesNotReroll() {
    val results = List(10) { SemanticActionMapper.resolve("Tắt cầu dao nguồn chính", candidates) }
    assertEquals(1, results.map { it.candidateToken to it.score }.distinct().size)
  }

  @Test fun mapperBoundaryContainsOnlyOpaqueTokenAndPublicDescriptions() {
    val descriptor = candidates.first().toString()
    assertFalse(descriptor.contains("requiredActions"))
    assertFalse(descriptor.contains("requiredFacts"))
    assertFalse(descriptor.contains("escapeBlueprint"))
    assertFalse(descriptor.contains("COMPLETE_LEVEL"))
    assertFalse(descriptor.contains("cut_power"))
  }
}
