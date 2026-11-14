const port = Number(process.env.PW_PORT ?? 4173);
if (!Number.isInteger(port) || port < 1024 || port > 65534) {
  throw new Error('PW_PORT must be an integer between 1024 and 65534');
}

export const readOnlyPort = port;
export const mutablePort = port + 1;
export const readOnlyURL = `http://127.0.0.1:${readOnlyPort}`;
export const mutableURL = `http://127.0.0.1:${mutablePort}`;
