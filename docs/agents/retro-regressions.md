# Регрессии ретроспективы #35

Продуктовые правила #13 не изменены. Ниже — существующие публичные проверки,
сохранённые вместо дублирующих тестов. Имена методов/сценариев — навигационные
ссылки; фактические результаты полного прогона публикуются отдельно в ticket.

| Инвариант                                                                                                                     | Реальная граница и существующее покрытие                                                                                                                                                                                                                                                                                                                                                       |
| ----------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Unicode: кодовые точки, границы длины, невидимые/управляющие символы, отсутствие частичных изменений                          | `ProfileEditHttpTests`: таблицы `invalidNameReturnsFieldErrorAndChangesNothing`, `invalidUsernameReturnsFieldErrorWithoutChangingNameOrLinks`, `boundaryNamesAndUsernamesReplaceLinksAndCanRestoreOldAddress`; `MeetingTypeHttpTests.descriptionCountsVisibleCodePointsWithoutRejectingAnOtherwiseLongEnoughDescription`; `BookingAttemptHttpTests.rejectsInvalidContactsWithoutReservation`   |
| Ключ идемпотентности: безопасные границы, неверный ключ без сохранения исхода, повтор до проверки версии, общий ledger команд | `PublicBookingCommandHttpTests`: таблицы `transportSafeBoundaryKeysRecoverSavedOutcomes`, `incompatibleKeyCannotChangeStateOrSaveOutcome`; `sameKeyReplaysSavedSuccessBeforeCheckingVersionAndDoesNotReapply`; `ProfileEditHttpTests.malformedCommandsOnDifferentEndpointsCannotShareAnOperationKey`; `MeetingTypeHttpTests.creationReplaysAndRecoversFromSharedLedgerEvenAfterProfileChanges` |
| Приоритет: неизвестный адрес раньше выключенной записи/архива/ошибок данных, public state раньше условий                      | `BookingAttemptHttpTests.addressAndPublicStateTakePriorityOverOtherPreparationErrors`; `SlotHttpTests.slotReadRejectsDisabledBookingAfterResolvingBothAddressParts`; `MeetingTypeHttpTests.publicTypesExposeShownConditionsOnlyWhenOpenAndUnknownAddressesRemain404`                                                                                                                           |
| Соседние формы не теряют полный черновик при восстановлении; нет повторной команды/частичного сохранения                      | `frontend/tests/meeting-types.spec.ts`: матрица `черновик типа целиком сохраняется` — profile/public-booking × успешная загрузка/ошибка и повтор после настоящего 410; настоящие серверные чтения/записи, сетевые обрывы, без подмены booking API данными                                                                                                                                      |

Новые инструментальные регрессии — `scripts/tests/*.test.mjs`:

- маршрут старого #15 `/public/entry` против `/public/{username}`, переименованные
  параметры и параметры в разных сегментах; разные длины/методы/литералы допустимы;
  namespace-маршруты разрешаются исходным TypeSpec compiler;
- comparator `api:check`: added/removed/changed/unchanged, неизменность файлов
  рабочего каталога после каждой проверки. Генерация остаётся настоящей в полном
  gate; фикстуры comparator не выдаются за запуск всей генерации;
- doctor: неверная версия через CLI PATH-фикстуру, отсутствующие команды,
  настоящий занятый порт и настоящий запуск установленного Chromium;
- checkpoint: публичные save/show, отсутствие tracked изменений, отказ при другом
  worktree, изменённых инструкциях/HEAD, недопустимых категориях и неуказанном review SHA.

При новом инварианте сначала добавить один воспроизводящий тест на соответствующей
границе. Не создавать будущие API для тестирования и не дублировать эту матрицу
ещё одним набором тех же запросов.
