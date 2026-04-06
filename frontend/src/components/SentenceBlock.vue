<template>
  <article
    :id="`sentence-${sentence.id}`"
    class="sentence-card"
    :class="{ 'sentence-card--read': isRead }"
    :data-sentence-id="sentence.id"
    ref="sentenceElement"
  >
    <div class="sentence-main">
      <p class="sentence-text">{{ sentence.text }}</p>
      <div v-if="isExplanationOpen" class="explanation-panel">
        {{ sentence.explanation }}
      </div>
    </div>
    <button class="toggle-button sentence-toggle" @click="toggleExplanation">
      {{ isExplanationOpen ? 'Hide Explanation' : 'Show Explanation' }}
    </button>
  </article>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  sentence: {
    type: Object,
    required: true
  },
  isExplanationOpen: {
    type: Boolean,
    required: true
  },
  isRead: {
    type: Boolean,
    required: true
  },
  observer: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['toggle-explanation'])
const sentenceElement = ref(null)

function toggleExplanation() {
  emit('toggle-explanation', props.sentence.id)
}

onMounted(() => {
  if (props.observer && sentenceElement.value) {
    props.observer.observe(sentenceElement.value)
  }
})

onBeforeUnmount(() => {
  if (props.observer && sentenceElement.value) {
    props.observer.unobserve(sentenceElement.value)
  }
})
</script>
