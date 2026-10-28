import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hello_flutter/models/recipe_recommendation.dart';
import 'package:hello_flutter/screens/recipe_detail_screen.dart';
import 'package:hello_flutter/services/fridge_api_service.dart';

RecipeRecommendation recipe({List<RecipeIngredientDetail>? ingredients}) =>
    RecipeRecommendation(
      id: 'rec_test',
      title: 'Lentil Soup',
      matchPercent: 50,
      rating: 4.2,
      ingredientTags: const ['Tomato', 'Lentils'],
      prepMinutes: 20,
      kcal: 250,
      slot: RecipeMealSlot.soup,
      description: 'Simmer until tender.',
      ingredientDetails:
          ingredients ??
          const [
            RecipeIngredientDetail(
              name: 'Tomato',
              quantityText: '1 count',
              inFridge: true,
            ),
            RecipeIngredientDetail(
              name: 'Lentils',
              quantityText: '100 g',
              inFridge: false,
            ),
          ],
    );

class FakeRecipeApi extends FridgeApiService {
  FakeRecipeApi(this.detail);
  RecipeRecommendation detail;
  Completer<RecipeRecommendation>? pending;
  int loadFailures = 0;
  int addFailures = 0;
  int lostResponses = 0;
  int plannedBatches = 0;
  FridgeApiException? addError;
  int loadCalls = 0;
  int addCalls = 0;

  @override
  Future<RecipeRecommendation> fetchRecipeDetail(String id) async {
    loadCalls++;
    if (loadFailures > 0) {
      loadFailures--;
      throw StateError('offline');
    }
    return pending == null ? detail : await pending!.future;
  }

  @override
  Future<({int addedCount, int mergedCount})> addMissingIngredientsToGrocery(
    String id,
  ) async {
    addCalls++;
    if (addError != null) throw addError!;
    if (addFailures > 0) {
      addFailures--;
      throw FridgeApiException(
        'request rejected',
        '{"code":"BAD_REQUEST"}',
        statusCode: 400,
      );
    }
    plannedBatches++;
    if (lostResponses > 0) {
      lostResponses--;
      throw StateError('connection closed after the server committed the plan');
    }
    return (addedCount: 1, mergedCount: 0);
  }
}

Future<void> showDetail(
  WidgetTester tester,
  FakeRecipeApi api, {
  bool live = true,
  Size size = const Size(430, 900),
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  addTearDown(api.close);
  await tester.pumpWidget(
    MaterialApp(
      home: RecipeDetailScreen(recipe: recipe(), useLiveApi: live, api: api),
    ),
  );
}

void main() {
  testWidgets('loads actual detail and does not invent fallback ingredients', (
    tester,
  ) async {
    final api = FakeRecipeApi(recipe())
      ..pending = Completer<RecipeRecommendation>();
    await showDetail(tester, api);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    api.pending!.complete(recipe());
    await tester.pumpAndSettle();
    expect(find.text('Tomato'), findsOneWidget);
    expect(find.text('Lentils'), findsOneWidget);
    expect(find.text('Whole Milk'), findsNothing);
    expect(find.text('Sourdough Bread'), findsNothing);
    expect(api.loadCalls, 1);
    expect(tester.takeException(), isNull);
  });

  testWidgets('adds missing ingredients once and confirms server result', (
    tester,
  ) async {
    final api = FakeRecipeApi(recipe());
    await showDetail(tester, api);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Add missing ingredients'));
    await tester.pumpAndSettle();
    expect(api.addCalls, 1);
    expect(
      find.text('1 added, 0 merged into your grocery list.'),
      findsOneWidget,
    );
    final button = tester.widget<ButtonStyleButton>(
      find
          .ancestor(
            of: find.text('Added to grocery list'),
            matching: find.byWidgetPredicate(
              (widget) => widget is ButtonStyleButton,
            ),
          )
          .first,
    );
    expect(button.onPressed, isNull);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'known rejected grocery addition remains retryable without false success',
    (tester) async {
      final api = FakeRecipeApi(recipe())..addFailures = 1;
      await showDetail(tester, api);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Add missing ingredients'));
      await tester.pumpAndSettle();
      expect(
        find.text('Could not add ingredients. Please try again.'),
        findsOneWidget,
      );
      expect(find.text('Added to grocery list'), findsNothing);
      await tester.tap(find.text('Add missing ingredients'));
      await tester.pumpAndSettle();
      expect(api.addCalls, 2);
      expect(find.text('Added to grocery list'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('detail load errors show retry instead of demo data', (
    tester,
  ) async {
    final api = FakeRecipeApi(recipe())..loadFailures = 1;
    await showDetail(tester, api);
    await tester.pumpAndSettle();
    expect(
      find.text('Could not load this recipe. Please try again.'),
      findsOneWidget,
    );
    expect(find.text('Tomato'), findsNothing);
    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(api.loadCalls, 2);
    expect(find.text('Tomato'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'empty recipe does not divide by zero or insert fake ingredients',
    (tester) async {
      final api = FakeRecipeApi(recipe(ingredients: const []));
      await showDetail(tester, api);
      await tester.pumpAndSettle();
      expect(find.text('No required ingredients listed'), findsOneWidget);
      expect(find.text('Whole Milk'), findsNothing);
      expect(find.text('Tomato'), findsNothing);
      expect(api.addCalls, 0);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('available ingredients disable the grocery action', (
    tester,
  ) async {
    final api = FakeRecipeApi(
      recipe(
        ingredients: const [
          RecipeIngredientDetail(
            name: 'Tomato',
            quantityText: '1 count',
            inFridge: true,
          ),
        ],
      ),
    );
    await showDetail(tester, api);
    await tester.pumpAndSettle();
    expect(
      find.text('All required ingredients are in your fridge'),
      findsOneWidget,
    );
    expect(find.text('Add missing ingredients'), findsNothing);
    expect(api.addCalls, 0);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'desktop live layout retains actual ingredients and functional action',
    (tester) async {
      final api = FakeRecipeApi(recipe());
      await showDetail(tester, api, size: const Size(1440, 1100));
      await tester.pumpAndSettle();
      expect(find.text('Add missing ingredients'), findsOneWidget);
      expect(find.text('Start Cooking →'), findsNothing);
      expect(find.text('Whole Milk'), findsNothing);
      await tester.tap(find.text('Add missing ingredients'));
      await tester.pumpAndSettle();
      expect(api.addCalls, 1);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('explicit demo mode does not contact the authenticated API', (
    tester,
  ) async {
    final api = FakeRecipeApi(recipe());
    await showDetail(tester, api, live: false);
    await tester.pumpAndSettle();
    expect(api.loadCalls, 0);
    expect(find.text('Add missing ingredients'), findsNothing);
    expect(tester.takeException(), isNull);
  });
  testWidgets(
    'quantity conflicts explain how to recover without false success',
    (tester) async {
      final api = FakeRecipeApi(recipe())
        ..addError = FridgeApiException(
          'planning failed',
          '{"code":"QUANTITY_LIMIT_EXCEEDED","message":"quantity limit"}',
          statusCode: 409,
        );
      await showDetail(tester, api);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Add missing ingredients'));
      await tester.pumpAndSettle();
      expect(
        find.text(
          'A grocery quantity is too high. Reduce it before adding this recipe.',
        ),
        findsOneWidget,
      );
      expect(find.text('Added to grocery list'), findsNothing);
      expect(api.addCalls, 1);
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets(
    'lost reply after mutation requires confirmation before another batch',
    (tester) async {
      final api = FakeRecipeApi(recipe())..lostResponses = 1;
      await showDetail(tester, api);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Add missing ingredients'));
      await tester.pumpAndSettle();
      expect(api.addCalls, 1);
      expect(api.plannedBatches, 1);
      expect(find.text('Added to grocery list'), findsNothing);
      expect(
        find.text('Could not add ingredients. Please try again.'),
        findsNothing,
      );
      final unconfirmed = tester.widget<ButtonStyleButton>(
        find
            .ancestor(
              of: find.text('Result unconfirmed'),
              matching: find.byWidgetPredicate(
                (widget) => widget is ButtonStyleButton,
              ),
            )
            .first,
      );
      expect(unconfirmed.onPressed, isNull);
      await tester.tap(find.text('Add another batch…'));
      await tester.pumpAndSettle();
      expect(find.text('Add another batch?'), findsOneWidget);
      expect(api.addCalls, 1);
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(api.addCalls, 1);
      expect(api.plannedBatches, 1);
      await tester.tap(find.text('Add another batch…'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Add another batch'));
      await tester.pumpAndSettle();
      expect(api.addCalls, 2);
      expect(api.plannedBatches, 2);
      expect(find.text('Added to grocery list'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'server errors leave the outcome unconfirmed instead of offering retry',
    (tester) async {
      final api = FakeRecipeApi(recipe())
        ..addError = FridgeApiException(
          'server unavailable',
          '',
          statusCode: 503,
        );
      await showDetail(tester, api);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Add missing ingredients'));
      await tester.pumpAndSettle();
      expect(api.addCalls, 1);
      expect(find.text('Result unconfirmed'), findsOneWidget);
      expect(find.text('Add missing ingredients'), findsNothing);
      expect(find.text('Add another batch…'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );
}
