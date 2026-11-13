import { compile, NodeHost, formatDiagnostic } from '@typespec/compiler';
import { getAllHttpServices } from '@typespec/http';
import { resolve } from 'node:path';

const program = await compile(
  NodeHost,
  resolve(process.argv[2] ?? 'api/main.tsp'),
  { noEmit: true },
);
const [services, diagnostics] = getAllHttpServices(program);
for (const diagnostic of [...program.diagnostics, ...diagnostics]) {
  console.error(formatDiagnostic(diagnostic));
  process.exitCode = 1;
}
const operations = services.flatMap((service) => service.operations);
for (let i = 0; i < operations.length; i++) {
  for (const right of operations.slice(i + 1)) {
    const left = operations[i];
    const a = left.path.split('/');
    const b = right.path.split('/');
    if (
      left.verb === right.verb &&
      a.length === b.length &&
      a.every(
        (segment, index) =>
          segment === b[index] ||
          /^\{[^}]+\}$/.test(segment) ||
          /^\{[^}]+\}$/.test(b[index]),
      )
    ) {
      console.error(
        `Overlapping ${left.verb.toUpperCase()} routes: ${left.path} (${left.operation.name}) and ${right.path} (${right.operation.name})`,
      );
      process.exitCode = 1;
    }
  }
}
if (!process.exitCode)
  console.log(
    `No overlapping routes (${operations.length} operations from TypeSpec source).`,
  );
