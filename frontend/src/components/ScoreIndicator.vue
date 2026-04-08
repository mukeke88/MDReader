<template>
  <div class="floating-score">
    <span class="score-label">Score</span>
    <div class="score-values">
      <label class="score-input-group">
        <span class="sr-only">Green score</span>
        <input
          class="score-input score-input--green"
          :value="greenScore"
          type="number"
          min="0"
          step="1"
          @input="updateScore('green', $event)"
        />
      </label>
      <span class="score-values__divider">/</span>
      <label class="score-input-group">
        <span class="sr-only">Red score</span>
        <span class="score-input-stack">
          <input
            class="score-input score-input--red"
            :value="redScore"
            type="number"
            min="0"
            step="1"
            @input="updateScore('red', $event)"
          />
          <span class="score-manual-badge">
            <span class="score-manual-badge__prefix">+</span>
            <input
              class="score-input score-input--manual-red"
              :value="manualRedScore"
              type="number"
              min="0"
              step="1"
              @input="updateScore('manual-red', $event)"
            />
          </span>
        </span>
      </label>
    </div>
    <p class="score-caption">Green / Red (+ phone)</p>
  </div>
</template>

<script setup>
const props = defineProps({
  greenScore: {
    type: Number,
    required: true
  },
  redScore: {
    type: Number,
    required: true
  },
  manualRedScore: {
    type: Number,
    required: true
  }
})

const emit = defineEmits(['update:greenScore', 'update:redScore', 'update:manualRedScore'])

function normalizeScore(value) {
  const parsed = Number.parseInt(value, 10)
  if (Number.isNaN(parsed)) {
    return 0
  }

  return Math.max(0, parsed)
}

function updateScore(type, event) {
  const normalizedValue = normalizeScore(event.target.value)
  event.target.value = normalizedValue

  if (type === 'green' && normalizedValue !== props.greenScore) {
    emit('update:greenScore', normalizedValue)
  }

  if (type === 'red' && normalizedValue !== props.redScore) {
    emit('update:redScore', normalizedValue)
  }

  if (type === 'manual-red' && normalizedValue !== props.manualRedScore) {
    emit('update:manualRedScore', normalizedValue)
  }
}
</script>
