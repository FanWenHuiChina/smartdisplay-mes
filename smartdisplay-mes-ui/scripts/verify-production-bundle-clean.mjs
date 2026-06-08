import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const projectRoot = resolve(scriptDir, '..')
const assetsDir = join(projectRoot, 'dist', 'assets')

const forbiddenTokens = [
  'LOT202406',
  'LOT260606',
  'MO202606',
  'COATER_02',
  'GW-SIM',
  'PI260606',
  'PI-ADH-240606',
  'RCP_COAT',
  'SOP-COAT',
  'EVT-FALLBACK',
  'EX-FALLBACK',
  'MRBT-FALLBACK',
  'COA-PI260606',
  'YIELD-20260606',
  'EQ-COATER02',
  'RAG-SOP-ASK',
  'QI-FALLBACK',
  'EPS-FALLBACK',
  'ECS-FALLBACK',
  'RDL-FALLBACK',
  'EGM-FALLBACK',
  'EGH-FALLBACK'
]

if (!existsSync(assetsDir)) {
  console.error('Production bundle check failed: dist/assets does not exist. Run npm run build first.')
  process.exit(1)
}

const jsFiles = readdirSync(assetsDir)
  .filter(file => file.endsWith('.js'))
  .map(file => join(assetsDir, file))

const findings = []
for (const file of jsFiles) {
  const source = readFileSync(file, 'utf8')
  for (const token of forbiddenTokens) {
    const index = source.indexOf(token)
    if (index >= 0) {
      findings.push({
        file,
        token,
        excerpt: source.slice(Math.max(0, index - 40), index + token.length + 40)
      })
    }
  }
}

if (findings.length) {
  console.error(`Production bundle check failed: ${findings.length} mock/fallback tokens found`)
  for (const finding of findings) {
    console.error(`- ${finding.file} :: ${finding.token} :: ${finding.excerpt}`)
  }
  process.exit(1)
}

console.log(`Production bundle clean: ${jsFiles.length} JS assets checked`)
