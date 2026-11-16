import http from 'k6/http';
import { check, sleep } from 'k6';

// Configuração do Teste de Carga (Load Test)
export const options = {
  stages: [
    // 1. Aquecimento: Sobe para 20 usuários virtuais em 15 segundos
    { duration: '15s', target: 20 },

    // 2. Pico de Estresse: Salta para 80 usuários simultâneos por 45 segundos
    // (É aqui que a CPU da API vai passar de 70% e o HPA vai disparar a criação de novas réplicas!)
    { duration: '45s', target: 80 },

    // 3. Resfriamento: Volta a carga para 0 em 15 segundos
    // (O HPA vai detectar a queda de tráfego e desescalar os pods de volta ao mínimo)
    { duration: '15s', target: 0 },
  ],
  thresholds: {
    // 95% das requisições devem responder em menos de 100ms (graças ao cache do Redis!)
    http_req_duration: ['p(95)<100'],
    // Menos de 1% de falhas
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = 'http://localhost:8080';

// Fase de Setup (Executa 1 única vez antes dos usuários virtuais entrarem):
// Garante que exista uma URL válida no banco e aquecida no Redis para os testes de leitura (GET)
export function setup() {
  const payload = JSON.stringify({
    url: 'https://github.com/lcvinicius/cloud-native-url-shortener',
  });
  const params = {
    headers: { 'Content-Type': 'application/json' },
  };

  const res = http.post(`${BASE_URL}/api/v1/urls`, payload, params);
  const body = res.json();
  return { shortCode: body && body.shortCode ? body.shortCode : 'fOrEfG5' };
}

export default function (data) {
  // Proporção de tráfego do mundo real:
  // 80% dos usuários clicam em links existentes (Leitura no Redis / Cache Hit)
  // 20% dos usuários criam novos links (Escrita no Postgres e Redis)

  if (Math.random() < 0.8) {
    // 1. Acessa o link encurtado criado no setup (Cache Hit no Redis < 2ms)
    const res = http.get(`${BASE_URL}/${data.shortCode}`, {
      redirects: 0, // Não segue o redirecionamento para medir o tempo exato da nossa API
    });

    check(res, {
      'status é 302 (Redirect com sucesso)': (r) => r.status === 302,
    });
  } else {
    // 2. Cria um novo link encurtado
    const payload = JSON.stringify({
      url: `https://github.com/lcvinicius/project-${Math.floor(Math.random() * 10000)}`,
    });

    const params = {
      headers: {
        'Content-Type': 'application/json',
      },
    };

    const res = http.post(`${BASE_URL}/api/v1/urls`, payload, params);

    check(res, {
      'status é 201 (Link criado)': (r) => r.status === 201,
    });
  }

  // Pausa realista entre 100ms e 300ms entre ações de um mesmo usuário
  sleep(0.2);
}

