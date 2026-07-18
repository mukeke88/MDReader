const fs = require('fs')
const path = require('path')

const repoRoot = path.resolve(__dirname, '..')
const dataDir = path.join(repoRoot, 'data')
const chaptersPath = path.join(dataDir, 'chapters.json')
const sentencesDir = path.join(dataDir, 'sentences')

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'))
}

function sqlString(value) {
  if (value === null || value === undefined) {
    return 'NULL'
  }

  return `'${String(value)
    .replace(/\\/g, '\\\\')
    .replace(/'/g, "''")
    .replace(/\r/g, '\\r')
    .replace(/\n/g, '\\n')}'`
}

const chapters = readJson(chaptersPath)

console.log('USE mdreader;')
console.log('')
console.log('SET FOREIGN_KEY_CHECKS = 0;')
console.log('TRUNCATE TABLE sentences;')
console.log('TRUNCATE TABLE chapters;')
console.log('SET FOREIGN_KEY_CHECKS = 1;')
console.log('')

for (const chapter of chapters) {
  console.log(
    'INSERT INTO chapters (id, book_id, title, source_file) VALUES ' +
      `(${sqlString(chapter.id)}, ${sqlString(chapter.bookId)}, ${sqlString(chapter.title)}, ${sqlString(chapter.sourceFile)});`
  )

  const sentenceFile = path.join(sentencesDir, chapter.sourceFile)
  if (!fs.existsSync(sentenceFile)) {
    console.error(`Skipping sentences for ${chapter.id}: ${sentenceFile} does not exist.`)
    continue
  }

  const sentences = readJson(sentenceFile)
  for (const sentence of sentences) {
    console.log(
      'INSERT INTO sentences (chapter_id, sentence_id, paragraph_id, text, explanation) VALUES ' +
        `(${sqlString(chapter.id)}, ${Number(sentence.id)}, ${Number(sentence.paragraphId || 1)}, ` +
        `${sqlString(sentence.text || '')}, ${sqlString(sentence.explanation || '')});`
    )
  }
}
