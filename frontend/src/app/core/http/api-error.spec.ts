import { HttpErrorResponse } from '@angular/common/http';

import { apiErrorMessage, readFieldViolations } from './api-error';

describe('apiErrorMessage', () => {
  const fallback = 'Não foi possível concluir. Tente novamente.';

  it('usa a mensagem estruturada de erros 4xx', () => {
    const error = new HttpErrorResponse({ status: 409, error: { message: 'E-mail já cadastrado' } });
    expect(apiErrorMessage(error, fallback)).toBe('E-mail já cadastrado');
  });

  it('nunca exibe conteúdo do servidor em falhas 5xx ou de rede', () => {
    expect(apiErrorMessage(new HttpErrorResponse({ status: 500, error: { message: 'detalhe interno' } }), fallback)).toBe(fallback);
    expect(apiErrorMessage(new HttpErrorResponse({ status: 502, error: '<html>proxy</html>' }), fallback)).toBe(fallback);
    expect(apiErrorMessage(new HttpErrorResponse({ status: 0 }), fallback)).toBe(fallback);
  });

  it('ignora corpos não estruturados mesmo em 4xx', () => {
    expect(apiErrorMessage(new HttpErrorResponse({ status: 404, error: '<pre>/srv/app</pre>' }), fallback)).toBe(fallback);
    expect(apiErrorMessage(new Error('falha'), fallback)).toBe(fallback);
  });

  it('extrai violações por campo válidas', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: { violations: [{ field: 'email', message: 'email deve ser valido' }, { field: 'x' }, null] },
    });
    expect(readFieldViolations(error)).toEqual([{ field: 'email', message: 'email deve ser valido' }]);
  });
});
