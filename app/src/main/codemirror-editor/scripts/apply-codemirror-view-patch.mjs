import { execFileSync } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const projectRoot = path.resolve(fileURLToPath(new URL('../', import.meta.url)));
const patchFile = path.join(projectRoot, 'patches/@codemirror+view+6.43.4.patch');
const patch = (await readFile(patchFile, 'utf8')).replace(/\r\n/g, '\n');
const packageFile = path.join(projectRoot, 'node_modules/@codemirror/view/package.json');
const version = JSON.parse(await readFile(packageFile, 'utf8')).version;

if (version !== '6.43.4') {
  throw new Error(`Expected @codemirror/view 6.43.4, found ${version}`);
}

const repoRoot = execFileSync('git', ['rev-parse', '--show-toplevel'], {
  cwd: projectRoot,
  encoding: 'utf8',
}).trim();

function apply(args) {
  return execFileSync('git', args, {
    cwd: repoRoot,
    encoding: 'utf8',
    input: patch,
    stdio: ['pipe', 'pipe', 'pipe'],
  });
}

try {
  apply(['apply', '--check', '--whitespace=error', '-']);
  apply(['apply', '--whitespace=error', '-']);
} catch (error) {
  try {
    apply(['apply', '--reverse', '--check', '--whitespace=error', '-']);
  } catch {
    const detail = error?.stderr?.toString().trim();
    throw new Error(`@codemirror/view 6.43.4 patch does not apply${detail ? `: ${detail}` : ''}`);
  }
}
