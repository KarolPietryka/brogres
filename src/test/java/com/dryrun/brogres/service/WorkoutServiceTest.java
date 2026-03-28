package com.dryrun.brogres.service;

import com.dryrun.brogres.data.Workout;
import com.dryrun.brogres.data.WorkoutResponseDtos.WorkoutExerciseViewDto;
import com.dryrun.brogres.data.WorkoutResponseDtos.WorkoutPrefillDto;
import com.dryrun.brogres.data.WorkoutSet;
import com.dryrun.brogres.data.WorkoutSubmitRequestDto;
import com.dryrun.brogres.data.PlanWorkoutSet;
import com.dryrun.brogres.mapper.PlanWorkoutSetMapper;
import com.dryrun.brogres.mapper.WorkoutSummaryMapper;
import com.dryrun.brogres.repo.PlanWorkoutSetRepository;
import com.dryrun.brogres.repo.WorkoutSetRepository;
import com.dryrun.brogres.repo.WorkoutRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Testy {@link WorkoutService}. Przy każdym {@code when}, {@code verify} i {@code assert*} jest komentarz intencji
 * (reguła workspace: {@code unit-test-intent-comments.mdc}).
 */
@ExtendWith(MockitoExtension.class)
class WorkoutServiceTest {

    // Mock: zależności WorkoutService — stubujemy odpowiedzi repozytoriów i fabryki.
    @Mock
    WorkoutFactory workoutFactory;

    @Mock
    WorkoutRepository workoutRepository;

    @Mock
    WorkoutSetRepository workoutSetRepository;

    @Mock
    PlanWorkoutSetRepository planWorkoutSetRepository;

    /**
     * Spy: prawdziwy wygenerowany MapStruct mapper — w testach tworzenia treningu wykonuje realne mapowanie
     * {@link PlanWorkoutSetMapper#fromWorkoutSets}; można też weryfikować interakcje, jeśli dodasz verify na spy.
     */
    @Spy
    private PlanWorkoutSetMapper planWorkoutSetMapper = Mappers.getMapper(PlanWorkoutSetMapper.class);

    @Spy
    private WorkoutSummaryMapper workoutSummaryMapper = Mappers.getMapper(WorkoutSummaryMapper.class);

    @InjectMocks
    WorkoutService workoutService;

    /** Captor: przechwytuje argument {@code save(Workout)} — asercje na dacie i id. */
    @Captor
    ArgumentCaptor<Workout> workoutCaptor;

    /** Captor: przechwytuje listę z {@code saveAll} dla {@link WorkoutSet}. */
    @Captor
    ArgumentCaptor<List<WorkoutSet>> setsCaptor;

    /** Captor: przechwytuje listę z {@code saveAll} dla {@link PlanWorkoutSet}. */
    @Captor
    ArgumentCaptor<List<PlanWorkoutSet>> planWorkoutSetsCaptor;

    /**
     * First workout of the day: creates a new {@link Workout}, persists it, saves all {@link WorkoutSet} rows from the DTO
     * in order, and returns the saved workout.
     */
    @Test
    void createWorkout_whenDtoProvided_savesWorkoutAndSetsWithExpectedValues() {
        /*
         JSON matching the DTO under test:

         {
           "bodyPart": [
             {
               "bodyPartName": "chest",
               "exercises": [
                 { "name": "Bench Press", "weight": 60.0, "reps": 8 },
                 { "name": "Bench Press", "weight": 65.0, "reps": 6 }
               ]
             },
             {
               "bodyPartName": "back",
               "exercises": [
                 { "name": "Pull-ups", "weight": null, "reps": 10 }
               ]
             }
           ]
         }
        */

        LocalDate today = LocalDate.now();

        WorkoutSubmitRequestDto request = new WorkoutSubmitRequestDto(List.of(
                new WorkoutSubmitRequestDto.WorkoutBodyPartDto("chest", List.of(
                        new WorkoutSubmitRequestDto.WorkoutExerciseDto("Bench Press", new BigDecimal("60.0"), 8),
                        new WorkoutSubmitRequestDto.WorkoutExerciseDto("Bench Press", new BigDecimal("65.0"), 6)
                )),
                new WorkoutSubmitRequestDto.WorkoutBodyPartDto("back", List.of(
                        new WorkoutSubmitRequestDto.WorkoutExerciseDto("Pull-ups", null, 10)
                ))
        ));

        Workout factoryWorkout = new Workout();
        // when: pierwszy trening dnia — fabryka zwraca świeży obiekt Workout przed zapisem.
        when(workoutFactory.createWorkout()).thenReturn(factoryWorkout);

        // when: w repozytorium nie ma jeszcze wiersza na dziś → gałąź „nowy Workout”.
        when(workoutRepository.findByWorkoutDate(today)).thenReturn(Optional.empty());
        // when: brak starszej sesji → kopiowanie planu (plan_workout_set) się nie wykona; stub musi zwrócić pusty Optional.
        when(workoutRepository.findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today)).thenReturn(Optional.empty());

        // when + thenAnswer: symulacja INSERT — Hibernate nadałby id; ustawiamy stałe 123L i zwracamy ten sam obiekt.
        when(workoutRepository.save(any(Workout.class))).thenAnswer(invocation -> {
            Workout w = invocation.getArgument(0);
            w.setId(123L);
            return w;
        });

        Workout result = workoutService.createWorkout(request);

        // verify: serwis musi najpierw sprawdzić, czy istnieje trening na dziś.
        verify(workoutRepository).findByWorkoutDate(today);

        // verify + captor: dokładnie jeden save Workout; przechwytujemy encję, żeby asercjami potwierdzić datę i id.
        verify(workoutRepository).save(workoutCaptor.capture());
        Workout savedWorkout = workoutCaptor.getValue();
        // assert: zapisany trening ma datę bieżącego dnia (logika „dzisiaj”).
        assertThat(savedWorkout.getWorkoutDate()).isEqualTo(today);
        // assert: id ustawione tak jak po zapisie do bazy (mock).
        assertThat(savedWorkout.getId()).isEqualTo(123L);

        // verify + captor: wszystkie serie z DTO idą jednym saveAll — łapiemy listę WorkoutSet do inspekcji pól.
        verify(workoutSetRepository).saveAll(setsCaptor.capture());
        List<WorkoutSet> savedWorkoutSets = setsCaptor.getValue();

        // assert: trzy linie DTO (2× bench + pull-up) → trzy wiersze WorkoutSet.
        assertThat(savedWorkoutSets).hasSize(3);
        // assert: każda seria należy do zapisanego workoutu i ma sensowne minimum (nazwa, repy).
        assertThat(savedWorkoutSets).allSatisfy(s -> {
            assertThat(s.getWorkout()).isSameAs(savedWorkout);
            assertThat(s.getExercise()).isNotBlank();
            assertThat(s.getRepetitions()).isPositive();
        });

        // assert: pierwsza seria — pierwszy wpis chest z DTO (60 kg × 8).
        assertThat(savedWorkoutSets.get(0).getExercise()).isEqualTo("Bench Press");
        assertThat(savedWorkoutSets.get(0).getBodyPart()).isEqualTo("chest");
        assertThat(savedWorkoutSets.get(0).getWeight()).isEqualByComparingTo(new BigDecimal("60.0"));
        assertThat(savedWorkoutSets.get(0).getRepetitions()).isEqualTo(8);

        // assert: druga seria — drugi wpis chest (rampa 65 kg × 6).
        assertThat(savedWorkoutSets.get(1).getExercise()).isEqualTo("Bench Press");
        assertThat(savedWorkoutSets.get(1).getBodyPart()).isEqualTo("chest");
        assertThat(savedWorkoutSets.get(1).getWeight()).isEqualByComparingTo(new BigDecimal("65.0"));
        assertThat(savedWorkoutSets.get(1).getRepetitions()).isEqualTo(6);

        // assert: trzecia seria — back, waga null (CW).
        assertThat(savedWorkoutSets.get(2).getExercise()).isEqualTo("Pull-ups");
        assertThat(savedWorkoutSets.get(2).getBodyPart()).isEqualTo("back");
        assertThat(savedWorkoutSets.get(2).getWeight()).isNull();
        assertThat(savedWorkoutSets.get(2).getRepetitions()).isEqualTo(10);

        // assert: lineOrder globalnie 0,1,2 — zgodnie z kolejnością w żądaniu.
        assertThat(savedWorkoutSets.get(0).getLineOrder()).isZero();
        assertThat(savedWorkoutSets.get(1).getLineOrder()).isEqualTo(1);
        assertThat(savedWorkoutSets.get(2).getLineOrder()).isEqualTo(2);

        // assert: API zwraca referencję do tego samego obiektu Workout co po save (kontrakt serwisu).
        assertThat(result).isSameAs(savedWorkout);

        // verify: przy tworzeniu dnia wywołane jest szukanie „ostatniej sesji przed dziś” (do planu; tu pusto).
        verify(workoutRepository).findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today);
        // verify: skoro nie było poprzedniej sesji, repozytorium planu nie zapisywało nic.
        verifyNoInteractions(planWorkoutSetRepository);

        // verify: żadnych dodatkowych wywołań na mockach poza ścieżką tego scenariusza.
        verifyNoMoreInteractions(workoutRepository, workoutSetRepository, workoutFactory);
    }

    /**
     * First workout of the day with history: after saving today's row, copies the previous session into
     * {@link PlanWorkoutSet} rows (drawer / prefill snapshot).
     */
    @Test
    void createWorkout_whenNewDayAndPreviousWorkoutExists_copiesPreviousSetsToPlanWorkoutSets() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        WorkoutSubmitRequestDto request = new WorkoutSubmitRequestDto(List.of(
                new WorkoutSubmitRequestDto.WorkoutBodyPartDto("chest", List.of(
                        new WorkoutSubmitRequestDto.WorkoutExerciseDto("Bench Press", new BigDecimal("60.0"), 8)
                ))
        ));

        Workout factoryWorkout = new Workout();
        // when: nowy dzień — fabryka dostarcza pusty Workout.
        when(workoutFactory.createWorkout()).thenReturn(factoryWorkout);
        // when: brak treningu na dziś przed zapisem.
        when(workoutRepository.findByWorkoutDate(today)).thenReturn(Optional.empty());

        Workout previous = new Workout();
        previous.setId(50L);
        previous.setWorkoutDate(yesterday);
        WorkoutSet prevSet = new WorkoutSet();
        prevSet.setId(100L);
        prevSet.setWorkout(previous);
        prevSet.setBodyPart("chest");
        prevSet.setExercise("Fly");
        prevSet.setWeight(new BigDecimal("40"));
        prevSet.setRepetitions(12);
        prevSet.setLineOrder(0);
        previous.getSets().add(prevSet);

        // when: jest „wczorajszy” trening z jedną serią — źródło kopiowania do planu dnia.
        when(workoutRepository.findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today)).thenReturn(Optional.of(previous));

        // when + thenAnswer: zapis dzisiejszego Workout z id jak z bazy (123L).
        when(workoutRepository.save(any(Workout.class))).thenAnswer(invocation -> {
            Workout w = invocation.getArgument(0);
            w.setId(123L);
            return w;
        });

        workoutService.createWorkout(request);

        // verify + captor: mapper skopiował poprzednią sesję do PlanWorkoutSet — łapiemy listę przekazaną do saveAll.
        verify(planWorkoutSetRepository).saveAll(planWorkoutSetsCaptor.capture());
        List<PlanWorkoutSet> savedPlan = planWorkoutSetsCaptor.getValue();
        // assert: jedna seria wczoraj → jeden wiersz planu.
        assertThat(savedPlan).hasSize(1);
        // assert: exercise i kolejność pochodzą z WorkoutSet poprzedniego dnia.
        assertThat(savedPlan.get(0).getExercise()).isEqualTo("Fly");
        assertThat(savedPlan.get(0).getLineOrder()).isZero();
        assertThat(savedPlan.get(0).getBodyPart()).isEqualTo("chest");
        // assert: plan przypięty do dzisiejszego workoutu o id ustawionym przez mock save.
        assertThat(savedPlan.get(0).getWorkout().getId()).isEqualTo(123L);

        // verify: serwis musiał pobrać poprzednią sesję do zbudowania planu.
        verify(workoutRepository).findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today);
    }

    /**
     * Workout for today already exists: append new sets only—no factory, no second {@code save(Workout)}—and return the
     * existing workout instance.
     */
    @Test
    void createWorkout_whenWorkoutForTodayAlreadyExists_appendsSetsToExistingWorkout() {
        LocalDate today = LocalDate.now();

        WorkoutSubmitRequestDto request = new WorkoutSubmitRequestDto(List.of(
                new WorkoutSubmitRequestDto.WorkoutBodyPartDto("chest", List.of(
                        new WorkoutSubmitRequestDto.WorkoutExerciseDto("Bench Press", new BigDecimal("60.0"), 8)
                ))
        ));

        Workout existing = new Workout();
        existing.setId(99L);
        existing.setWorkoutDate(today);

        // when: trening na dziś już istnieje (id 99) — dopisujemy serie, nie tworzymy nowego Workout.
        when(workoutRepository.findByWorkoutDate(today)).thenReturn(Optional.of(existing));
        // when: brak jeszcze serii → max lineOrder = -1, pierwsza nowa seria dostanie indeks 0.
        when(workoutSetRepository.findMaxLineOrderIndex(99L)).thenReturn(-1);

        Workout result = workoutService.createWorkout(request);

        // verify: serwis musi odczytać dzisiejszy workout.
        verify(workoutRepository).findByWorkoutDate(today);
        // verify: musi policzyć następny lineOrder dla istniejącego id treningu.
        verify(workoutSetRepository).findMaxLineOrderIndex(99L);
        // verify: przy append nie tworzymy nowego Workout — fabryka nie jest używana.
        verifyNoInteractions(workoutFactory);
        // verify: drugi save(Workout) nie jest wywoływany (tylko dopisywanie setów).
        verify(workoutRepository, never()).save(any(Workout.class));

        // verify + captor: jedna seria z DTO → saveAll z jednym WorkoutSet.
        verify(workoutSetRepository).saveAll(setsCaptor.capture());
        List<WorkoutSet> saved = setsCaptor.getValue();
        // assert: jedna linia w żądaniu → jeden zapisany WorkoutSet.
        assertThat(saved).hasSize(1);
        WorkoutSet persisted = saved.get(0);
        // assert: FK wskazuje na istniejący workout tego dnia, nie na nowy obiekt.
        assertThat(persisted.getWorkout()).isSameAs(existing);
        // assert: bodyPart i pola ćwiczenia zgodne z DTO.
        assertThat(persisted.getBodyPart()).isEqualTo("chest");
        assertThat(persisted.getExercise()).isEqualTo("Bench Press");
        assertThat(persisted.getWeight()).isEqualByComparingTo(new BigDecimal("60.0"));
        assertThat(persisted.getRepetitions()).isEqualTo(8);
        assertThat(persisted.getLineOrder()).isZero();
        // assert: zwracany Workout to ten sam co w bazie (append).
        assertThat(result).isSameAs(existing);

        // verify: przy append nie tworzymy ani nie aktualizujemy plan_workout_set (plan powstał przy pierwszym POST dnia).
        verifyNoInteractions(planWorkoutSetRepository);
        verifyNoMoreInteractions(workoutRepository, workoutSetRepository, workoutFactory);
    }

    /**
     * Prefill when a workout is already stored for today: returns persisted {@link PlanWorkoutSet} snapshot (drawer feed),
     * not empty — user continues the same session with the same plan reference.
     */
    @Test
    void prefillWorkout_whenWorkoutAlreadyExistsForToday_returnsPlanFromPlanWorkoutSets() {
        LocalDate today = LocalDate.now();
        // when: jest już trening na dziś → prefill z planu (PlanWorkoutSet), nie z historii „wczoraj”.
        when(workoutRepository.existsByWorkoutDate(today)).thenReturn(true);

        Workout todayW = new Workout();
        todayW.setId(5L);
        todayW.setWorkoutDate(today);
        PlanWorkoutSet pl = new PlanWorkoutSet();
        pl.setId(1L);
        pl.setWorkout(todayW);
        pl.setBodyPart("chest");
        pl.setExercise("Bench");
        pl.setWeight(new BigDecimal("50"));
        pl.setRepetitions(5);
        pl.setLineOrder(0);
        todayW.getPlanWorkoutSets().add(pl);

        // when: repozytorium zwraca dzisiejszy workout z załadowanymi planWorkoutSets (jak @EntityGraph w produkcji).
        when(workoutRepository.findWithPlanWorkoutSetsByWorkoutDate(today)).thenReturn(Optional.of(todayW));

        WorkoutPrefillDto result = workoutService.prefillWorkout();

        // assert: jedna grupa partii w odpowiedzi prefill.
        assertThat(result.bodyPart()).hasSize(1);
        // assert: nazwa grupy i jedna seria zgodna z PlanWorkoutSet (orderId = lineOrder).
        assertThat(result.bodyPart().get(0).bodyPartName()).isEqualTo("chest");
        assertThat(result.bodyPart().get(0).exercises()).containsExactly(
                new WorkoutExerciseViewDto("Bench", 0, new BigDecimal("50"), 5));

        // verify: najpierw sprawdzenie „czy jest dziś”.
        verify(workoutRepository).existsByWorkoutDate(today);
        // verify: potem załadowanie planu z dzisiejszego workoutu.
        verify(workoutRepository).findWithPlanWorkoutSetsByWorkoutDate(today);
        // verify: nie ma ścieżki „ostatni trening przed dziś” (inna gałąź).
        verify(workoutRepository, never()).findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(any());
        // verify: prefill tylko czyta — nie zapisuje setów ani planu.
        verifyNoInteractions(workoutFactory, workoutSetRepository, planWorkoutSetRepository);
        verifyNoMoreInteractions(workoutRepository);
    }

    @Test
    void prefillWorkout_whenWorkoutExistsForTodayButPlanEmpty_returnsEmptyBodyPart() {
        LocalDate today = LocalDate.now();
        // when: jest trening dziś, ale bez wierszy planu (np. migracja / brak historii przy utworzeniu).
        when(workoutRepository.existsByWorkoutDate(today)).thenReturn(true);

        Workout todayW = new Workout();
        todayW.setId(5L);
        todayW.setWorkoutDate(today);
        when(workoutRepository.findWithPlanWorkoutSetsByWorkoutDate(today)).thenReturn(Optional.of(todayW));

        WorkoutPrefillDto result = workoutService.prefillWorkout();
        // assert: z pustym planem mapowanie daje pustą listę bodyPart.
        assertThat(result.bodyPart()).isEmpty();

        verify(workoutRepository).existsByWorkoutDate(today);
        verify(workoutRepository).findWithPlanWorkoutSetsByWorkoutDate(today);
        verifyNoInteractions(workoutFactory, workoutSetRepository, planWorkoutSetRepository);
    }

    /**
     * Prefill when there is no workout today but history exists: map the latest workout with {@code workoutDate} strictly
     * before today into {@code bodyPart} groups, preserving set order by {@code lineOrder}.
     */
    @Test
    void prefillWorkout_whenNoWorkoutTodayButEarlierWorkoutExists_mapsSetsToBodyPartStructure() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        Workout previous = new Workout();
        previous.setId(7L);
        previous.setWorkoutDate(yesterday);

        WorkoutSet s1 = new WorkoutSet();
        s1.setId(10L);
        s1.setWorkout(previous);
        s1.setBodyPart("chest");
        s1.setExercise("Bench Press");
        s1.setWeight(new BigDecimal("60.0"));
        s1.setRepetitions(8);
        s1.setLineOrder(0);

        WorkoutSet s2 = new WorkoutSet();
        s2.setId(11L);
        s2.setWorkout(previous);
        s2.setBodyPart("chest");
        s2.setExercise("Bench Press");
        s2.setWeight(new BigDecimal("65.0"));
        s2.setRepetitions(6);
        s2.setLineOrder(1);

        WorkoutSet s3 = new WorkoutSet();
        s3.setId(12L);
        s3.setWorkout(previous);
        s3.setBodyPart("back");
        s3.setExercise("Pull-ups");
        s3.setWeight(null);
        s3.setRepetitions(10);
        s3.setLineOrder(2);

        previous.getSets().addAll(List.of(s3, s1, s2));

        // when: brak treningu na dziś.
        when(workoutRepository.existsByWorkoutDate(today)).thenReturn(false);
        // when: ostatnia sesja przed dziś (wczoraj) z trzema seriami — kolejność w liście celowo pomieszana względem lineOrder.
        when(workoutRepository.findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today))
                .thenReturn(Optional.of(previous));

        WorkoutPrefillDto result = workoutService.prefillWorkout();

        // assert: po sortowaniu po lineOrder serwis grupuje do dwóch bodyPart (chest, back).
        assertThat(result.bodyPart()).hasSize(2);

        // assert: pierwsza grupa — chest, dwie serie w kolejności lineOrder 0 i 1 (nie kolejności id w liście).
        assertThat(result.bodyPart().get(0).bodyPartName()).isEqualTo("chest");
        assertThat(result.bodyPart().get(0).exercises()).containsExactly(
                new WorkoutExerciseViewDto("Bench Press", 0, new BigDecimal("60.0"), 8),
                new WorkoutExerciseViewDto("Bench Press", 1, new BigDecimal("65.0"), 6)
        );

        // assert: druga grupa — back, jedna seria, waga null.
        assertThat(result.bodyPart().get(1).bodyPartName()).isEqualTo("back");
        assertThat(result.bodyPart().get(1).exercises()).containsExactly(
                new WorkoutExerciseViewDto("Pull-ups", 2, null, 10)
        );

        // verify: exists + findFirst dla „ostatniego przed dziś”.
        verify(workoutRepository).existsByWorkoutDate(today);
        verify(workoutRepository).findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today);
        // verify: prefill nie zapisuje nic do fabryki ani repozytoriów setów.
        verifyNoInteractions(workoutFactory, workoutSetRepository);
        verifyNoMoreInteractions(workoutRepository);
    }

    /**
     * Prefill cold start (or no row older than today): {@code exists} is false and {@code findFirst...LessThan} returns
     * empty—result is an empty {@code bodyPart} list.
     */
    @Test
    void prefillWorkout_whenNoWorkoutTodayAndRepositoryFindsNoEarlierWorkout_returnsEmptyBodyPartList() {
        LocalDate today = LocalDate.now();
        // when: cold start — nie ma treningu na dziś.
        when(workoutRepository.existsByWorkoutDate(today)).thenReturn(false);
        // when: w bazie nie ma żadnej sesji starszej niż dziś.
        when(workoutRepository.findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today))
                .thenReturn(Optional.empty());

        WorkoutPrefillDto result = workoutService.prefillWorkout();

        // assert: brak źródła do sklonowania → pusty bodyPart.
        assertThat(result.bodyPart()).isEmpty();

        verify(workoutRepository).existsByWorkoutDate(today);
        verify(workoutRepository).findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today);
        verifyNoInteractions(workoutFactory, workoutSetRepository);
        verifyNoMoreInteractions(workoutRepository);
    }

    /**
     * Edge case: {@code findFirst...LessThan(today)} returns a {@link Workout} with an empty {@code sets} collection.
     * Prefill is built only from sets, so the response is still {@code bodyPart: []}—same as cold start from the FE’s
     * perspective, but the service did run both repository calls (unlike {@link #prefillWorkout_whenNoWorkoutTodayAndRepositoryFindsNoEarlierWorkout_returnsEmptyBodyPartList}
     * where the second call returns {@link Optional#empty()}). Covers inconsistent data or a future “empty session” row.
     */
    @Test
    void prefillWorkout_whenEarlierWorkoutHasNoSets_returnsEmptyBodyPartList() {
        LocalDate today = LocalDate.now();

        // Workout row exists for a past date, but no child sets (orphan / incomplete session).
        Workout shell = new Workout();
        shell.setId(1L);
        shell.setWorkoutDate(today.minusDays(3));

        // when: nie ma dziś treningu.
        when(workoutRepository.existsByWorkoutDate(today)).thenReturn(false);
        // when: jest „ostatni przed dziś”, ale bez zestawów serii — edge case danych.
        when(workoutRepository.findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today))
                .thenReturn(Optional.of(shell));

        WorkoutPrefillDto result = workoutService.prefillWorkout();

        // assert: puste sets → brak ćwiczeń w bodyPart mimo że findFirst coś zwrócił.
        assertThat(result.bodyPart()).isEmpty();

        // verify: nadal wykonane są oba odczyty (exists + findFirst), jak przy pełnym prefillu.
        verify(workoutRepository).existsByWorkoutDate(today);
        verify(workoutRepository).findFirstByWorkoutDateLessThanOrderByWorkoutDateDesc(today);
        verifyNoInteractions(workoutFactory, workoutSetRepository);
        verifyNoMoreInteractions(workoutRepository);
    }
}